(ns isaac.cli.chat.single-turn
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [isaac.channel :as channel]
    [isaac.channel.cli :as cli-channel]
    [isaac.cli.chat.dispatch :as dispatch]
    [isaac.session.logging :as logging]
    [isaac.context.manager :as ctx]
    [isaac.logger :as log]
    [isaac.prompt.anthropic :as anthropic-prompt]
    [isaac.prompt.builder :as prompt]
    [isaac.session.bridge :as bridge]
    [isaac.session.compaction :as compaction]
    [isaac.session.context :as session-ctx]
    [isaac.session.storage :as storage]
    [isaac.tool.builtin :as builtin]
    [isaac.tool.registry :as tool-registry]))

;; region ----- Error Formatting -----

(defn- body-error-message [result]
  (let [body       (:body result)
        body-error (:error body)]
    (cond
      (map? body-error) (str (or (:type body-error) (name (:error result)))
                             ": "
                             (or (:message body-error) body-error))
      (string? body-error) body-error
      (map? body)          (pr-str body))))

(defn error-message [result]
  (or (:message result)
      (body-error-message result)
      (when (:status result)
        (str "HTTP " (:status result) " " (name (:error result))
             (when-let [body (:body result)]
               (str " - " (pr-str body)))))
      (let [error (:error result)]
        (if (keyword? error) (name error) (str error)))))

;; endregion ^^^^^ Error Formatting ^^^^^

;; region ----- Token Accounting -----

(defn extract-tokens [result]
  (let [resp  (:response result)
        usage (or (:token-counts result) (:usage resp) {})]
    {:inputTokens  (or (:inputTokens usage) (:prompt_eval_count resp) 0)
     :outputTokens (or (:outputTokens usage) (:eval_count resp) 0)
     :cacheRead    (:cacheRead usage)
     :cacheWrite   (:cacheWrite usage)}))

;; endregion ^^^^^ Token Accounting ^^^^^

;; region ----- Response Persistence -----

(defonce in-flight-compactions (atom {}))

(defn clear-async-compactions! []
  (reset! in-flight-compactions {}))

(defn- active-compaction-state [key-str]
  (get @in-flight-compactions key-str))

(defn async-compaction-in-flight? [key-str]
  (boolean (active-compaction-state key-str)))

(defn await-async-compaction! [key-str]
  (when-let [state (get @in-flight-compactions key-str)]
    (when-let [splice-ready (:splice-ready state)]
      (deliver splice-ready true))
    (let [future* (:future state)
          result  (deref future* 30000 ::timeout)]
      (when (= ::timeout result)
        (throw (ex-info "async compaction did not complete within 30 seconds" {:session key-str})))
      (swap! in-flight-compactions dissoc key-str)
      result)))

(defn- with-transcript-lock [key-str f]
  (if-let [lock (:lock (active-compaction-state key-str))]
    (locking lock (f))
    (f)))

(defn- append-message! [sdir key-str message]
  (with-transcript-lock key-str #(storage/append-message! sdir key-str message)))

(defn- append-error! [sdir key-str error-entry]
  (with-transcript-lock key-str #(storage/append-error! sdir key-str error-entry)))

(defn run-tool-calls! [sdir key-str tool-results]
  (doseq [[tc result] tool-results]
    (append-message! sdir key-str
                     {:role    "assistant"
                      :content [{:type      "toolCall"
                                 :id        (:id tc)
                                 :name      (:name tc)
                                 :arguments (:arguments tc)}]})
    (let [error? (str/starts-with? (str result) "Error:")]
      (append-message! sdir key-str
                       (cond-> {:role "toolResult" :id (:id tc) :content result}
                         error? (assoc :isError true))))))

(defn- normalized-error [err]
  (if (string? err) (keyword err) err))

(defn- persisted-error [err]
  (let [normalized (normalized-error err)]
    (if (keyword? normalized) (str normalized) normalized)))

(defn- store-error! [sdir key-str result {:keys [model provider]}]
  (try
    (append-error! sdir key-str
                   {:content  (error-message result)
                    :error    (persisted-error (:error result))
                    :model    model
                    :provider provider})
    (catch Exception e
      (log/warn :chat/error-not-stored
                :session key-str
                :provider provider
                :error (.getMessage e)))))

(defn- log-response-failed! [key-str provider result]
  (log/error :chat/response-failed
             :session key-str
             :provider provider
             :error (:error result)
             :message (error-message result)))

(defn- report-error! [sdir key-str provider result opts]
  (log-response-failed! key-str provider result)
  (store-error! sdir key-str result opts)
  result)

(defn- response-model [result model]
  (or (get-in result [:response :model]) model))

(defn- store-response! [sdir key-str result {:keys [model provider]}]
  (let [tokens         (extract-tokens result)
        resolved-model (response-model result model)]
    (logging/log-message-stored! key-str resolved-model tokens)
    (append-message! sdir key-str
                     {:role     "assistant"
                      :content  (or (:content result)
                                    (get-in result [:response :message :content]))
                      :model    resolved-model
                      :provider provider})
    (storage/update-tokens! sdir key-str tokens)))

(defn process-response! [sdir key-str result {:keys [model provider]}]
  (if (:error result)
    (report-error! sdir key-str provider result {:model model :provider provider})
    (store-response! sdir key-str result {:model model :provider provider})))

;; endregion ^^^^^ Response Persistence ^^^^^

;; region ----- Streaming -----

(defn- chunk-content [chunk]
  (let [content (or (get-in chunk [:message :content])
                    (get-in chunk [:delta :text])
                    (get-in chunk [:choices 0 :delta :content]))]
    (cond
      (string? content) content
      (vector? content) (apply str content)
      (nil? content)    nil
      :else             (str content))))

(defn- chunk-piece [full-content chunk]
  (when-let [content (chunk-content chunk)]
    (if (and (:done chunk)
             (seq full-content)
             (str/starts-with? content full-content))
      (subs content (count full-content))
      content)))

(defn stream-response! [provider provider-config request on-chunk]
  (let [full-content (atom "")
        final-resp   (atom nil)
        result       (dispatch/dispatch-chat-stream provider provider-config request
                                                    (fn [chunk]
                                                      (when-let [piece (chunk-piece @full-content chunk)]
                                                        (when (seq piece)
                                                          (swap! full-content str piece)
                                                          (on-chunk piece)))
                                                      (when (:done chunk)
                                                        (reset! final-resp chunk))))]
    (if (:error result)
      result
      {:content  (or (not-empty @full-content) (get-in result [:message :content]) "")
       :response (or @final-resp result)})))

(defn print-streaming-response [provider provider-config request]
  (let [result (stream-response! provider provider-config request
                                 (fn [chunk]
                                   (print chunk)
                                   (flush)))]
    (println)
    result))

(defn- emit-response-content! [channel-impl session-key response]
  (let [content (get-in response [:message :content])
        chunks  (cond
                  (vector? content) (mapv str content)
                  (string? content) [content]
                  (nil? content)    []
                  :else             [(str content)])]
    (doseq [chunk chunks]
      (channel/on-text-chunk channel-impl session-key chunk))
    (apply str chunks)))

(defn- stream-result [channel-impl session-key provider provider-config request recording-tool-fn]
  (if (:tools request)
    (let [result (dispatch/dispatch-chat-with-tools provider provider-config request recording-tool-fn)]
      (if (:error result)
        result
        (let [response (:response result)]
          {:content  (emit-response-content! channel-impl session-key response)
           :response response})))
    (if (identical? channel-impl cli-channel/channel)
      (print-streaming-response provider provider-config request)
      (let [result (dispatch/dispatch-chat provider provider-config request)]
        (if (:error result)
          result
          {:content  (emit-response-content! channel-impl session-key result)
           :response result})))))

(defn- tool-loop-call [raw-tool]
  {:arguments (get-in raw-tool [:function :arguments])
   :id        (or (:id raw-tool) (str (random-uuid)))
   :name      (get-in raw-tool [:function :name])
   :raw       raw-tool})

(defn- openai-compatible-provider? [provider provider-config]
  (= "openai-compatible" (dispatch/resolve-api provider provider-config)))

(defn- assistant-tool-loop-message [provider provider-config content tool-calls]
  (if (openai-compatible-provider? provider provider-config)
    {:role       "assistant"
     :content    (or content "")
     :tool_calls (mapv (fn [{:keys [arguments id name]}]
                         {:id       id
                          :type     "function"
                          :function {:arguments (if (string? arguments) arguments (json/generate-string arguments))
                                     :name      name}})
                       tool-calls)}
    {:role       "assistant"
     :content    (or content "")
     :tool_calls (mapv :raw tool-calls)}))

(defn- tool-result-loop-message [provider provider-config tool-call result]
  (if (openai-compatible-provider? provider provider-config)
    {:role         "tool"
     :tool_call_id (:id tool-call)
     :content      (str result)}
    {:role    "tool"
     :content (str result)}))

(defn- tool-loop-request [provider provider-config req result recording-tool-fn]
  (let [tool-calls     (mapv tool-loop-call (get-in (:response result) [:message :tool_calls]))
        assistant-msg  (assistant-tool-loop-message provider provider-config (:content result) tool-calls)
        result-msgs    (mapv (fn [tool-call]
                               (tool-result-loop-message provider provider-config tool-call
                                                         (recording-tool-fn (:name tool-call) (:arguments tool-call))))
                             tool-calls)]
    (into (:messages req) (cons assistant-msg result-msgs))))

(defn- stream-and-handle-tools!
  "Streaming loop with optional tool call detection and execution.
   If recording-tool-fn is nil, tool calls in the response are not handled."
  ([provider provider-config request recording-tool-fn]
   (stream-and-handle-tools! cli-channel/channel nil provider provider-config request recording-tool-fn))
  ([channel-impl session-key provider provider-config request recording-tool-fn]
    (loop [req   request
           loops 0
           last-loop-request nil]
      (let [result (stream-result channel-impl session-key provider provider-config req recording-tool-fn)]
        (if (:error result)
          result
          (let [raw-tools (get-in (:response result) [:message :tool_calls])]
            (if (and (seq raw-tools) recording-tool-fn (< loops 10))
              (let [new-messages (tool-loop-request provider provider-config req result recording-tool-fn)
                    next-req     (assoc req :messages new-messages)]
                (recur next-req (inc loops) next-req))
              (cond-> result
                last-loop-request (assoc :loop-request last-loop-request)))))))))

;; endregion ^^^^^ Streaming ^^^^^

;; region ----- Context Compaction -----

(defn- session-entry [sdir key-str]
  (storage/get-session sdir key-str))

(def ^:private max-compaction-attempts 5)

(defn- reserve-async-compaction! [key-str]
  (let [lock     (Object.)
        claimed? (atom false)]
    (swap! in-flight-compactions
           (fn [state]
             (if (contains? state key-str)
               state
               (do
                 (reset! claimed? true)
                 (assoc state key-str {:lock lock})))))
    (when @claimed? lock)))

(declare run-compaction-check!)

(defn- perform-compaction! [sdir key-str attempt total-tokens {:keys [channel compaction-llm-done context-window model provider provider-config soul splice-ready transcript-lock]}]
  (cond
    (> attempt max-compaction-attempts)
    (log/warn :session/compaction-stopped
              :session key-str
              :provider provider
              :model model
              :reason :max-attempts
              :attempt attempt
              :totalTokens total-tokens
              :context-window context-window)

    :else
    (do
      (logging/log-compaction-started! key-str provider model total-tokens context-window)
      (when channel
        (channel/on-text-chunk channel key-str "compacting..."))
      (let [result (ctx/compact! sdir key-str
                                 {:model                model
                                  :provider-config      provider-config
                                  :soul                 soul
                                  :context-window       context-window
                                  :transcript-lock      transcript-lock
                                  :compaction-llm-done  compaction-llm-done
                                  :splice-ready         splice-ready
                                  :chat-fn              (partial dispatch/dispatch-chat provider provider-config)})]
        (if (:error result)
          (log/error :session/compaction-failed :session key-str)
          (let [updated-total (:totalTokens (session-entry sdir key-str) 0)]
            (if (>= updated-total total-tokens)
              (log/warn :session/compaction-stopped
                        :session key-str
                        :provider provider
                        :model model
                        :reason :no-progress
                        :attempt attempt
                        :totalTokens updated-total
                        :context-window context-window)
              (run-compaction-check! sdir key-str
                                     {:channel         channel
                                      :context-window  context-window
                                      :model           model
                                      :provider        provider
                                      :provider-config provider-config
                                      :soul            soul
                                      :transcript-lock transcript-lock}
                                     (inc attempt)
                                     false))))))))

(defn- start-async-compaction! [sdir key-str opts]
  (when-let [lock (reserve-async-compaction! key-str)]
    (let [compaction-llm-done (promise)
          splice-ready        (promise)
          future*             (future
                                (run-compaction-check! sdir key-str
                                                       (assoc opts
                                                              :transcript-lock     lock
                                                              :compaction-llm-done compaction-llm-done
                                                              :splice-ready        splice-ready)
                                                       1 false))]
      (swap! in-flight-compactions assoc key-str {:future              future*
                                                   :lock                lock
                                                   :compaction-llm-done compaction-llm-done
                                                   :splice-ready        splice-ready})
      future*)))

(defn- run-compaction-check! [sdir key-str {:keys [context-window model provider] :as opts} attempt allow-async?]
  (let [entry        (session-entry sdir key-str)
        total-tokens (:totalTokens entry 0)
        config       (compaction/resolve-config entry context-window)]
    (logging/log-compaction-check! key-str provider model total-tokens context-window)
    (when (ctx/should-compact? entry context-window)
      (if (and allow-async? (:async? config))
        (start-async-compaction! sdir key-str opts)
        (perform-compaction! sdir key-str attempt total-tokens opts)))))

(defn check-compaction! [sdir key-str opts]
  (run-compaction-check! sdir key-str opts 1 true))

;; endregion ^^^^^ Context Compaction ^^^^^

;; region ----- Request Building -----

(defn- tool-capable-provider? [provider provider-config]
  (not (contains? #{"claude-sdk"} (dispatch/resolve-api provider provider-config))))

(defn- allowed-tool-names [crew-members crew-id]
  (when-let [crew (get crew-members crew-id)]
    (when (contains? crew :tools)
      (->> (get-in crew [:tools :allow])
           (mapv (fn [tool]
                   (cond
                     (keyword? tool) (name tool)
                     (string? tool)  tool
                     :else           (str tool))))
           set))))

(defn- active-tools [provider provider-config allowed-tools]
  (when (tool-capable-provider? provider provider-config)
    (not-empty (tool-registry/tool-definitions allowed-tools))))

(defn- ensure-default-tools-registered! []
  (when (empty? (tool-registry/all-tools))
    (builtin/register-all! tool-registry/register!)))

(defn build-chat-request [provider provider-config {:keys [boot-files model soul transcript tools]}]
  (let [build-fn (if (= "anthropic-messages" (dispatch/resolve-api provider provider-config))
                   anthropic-prompt/build
                    prompt/build)
        p        (build-fn {:boot-files boot-files :model model :soul soul :transcript transcript :tools tools :provider provider})]
    (cond-> {:model (:model p) :messages (:messages p)}
      (:system p)     (assoc :system (:system p))
      (:max_tokens p) (assoc :max_tokens (:max_tokens p))
      (:tools p)      (assoc :tools (:tools p)))))

;; endregion ^^^^^ Request Building ^^^^^

;; region ----- Public API -----

(defn process-user-input!
  [sdir key-str input {:keys [channel context-window crew-members model models provider provider-config soul]
                         :or   {channel cli-channel/channel}}]
  (let [turn          (bridge/begin-turn! key-str)
        session       (storage/get-session sdir key-str)
        crew-id       (or (:crew session) (:agent session) "main")
        turn-ctx      (session-ctx/resolve-turn-context {:agents crew-members
                                                         :models models
                                                         :cwd    (:cwd session)
                                                         :home   sdir}
                                                        crew-id)
         provider-cfg' (assoc (or provider-config {}) :session-key key-str)
         allowed-tools  (allowed-tool-names crew-members crew-id)
         ctx           {:crew           crew-id
                        :agent          crew-id
                        :crew-members   crew-members
                       :boot-files     (:boot-files turn-ctx)
                       :context-window context-window
                       :model          model
                       :models         models
                       :provider       provider
                       :soul           soul}
         finish-turn   (fn [result]
                        (when (and (:error result) (not= :cancelled (:error result)))
                          (channel/on-error channel key-str result))
                        (channel/on-turn-end channel key-str result)
                        result)]
    (try
     (channel/on-turn-start channel key-str input)
      (ensure-default-tools-registered!)
      (if (bridge/cancelled? key-str)
        (finish-turn (bridge/cancelled-result))
        (let [bridge-result (bridge/dispatch sdir key-str input ctx nil)]
          (if (= :command (:type bridge-result))
            (let [command-output (case (:command bridge-result)
                                   :status (bridge/format-status (:data bridge-result))
                                   (:message bridge-result))]
              (println command-output)
              (finish-turn bridge-result))
            (do
               (check-compaction! sdir key-str {:boot-files      (:boot-files turn-ctx)
                                                :model           model
                                                :soul            soul
                                                :context-window  context-window
                                                :provider        provider
                                                :provider-config provider-cfg'
                                                :channel         channel})
               (if (bridge/cancelled? key-str)
                 (finish-turn (bridge/cancelled-result))
                 (do
                   (append-message! sdir key-str {:role "user" :content input})
                   (let [transcript        (with-transcript-lock key-str #(storage/get-transcript sdir key-str))
                         tools             (active-tools provider provider-cfg' allowed-tools)
                         request           (build-chat-request provider provider-cfg'
                                                               {:boot-files (:boot-files turn-ctx)
                                                                :model      model
                                                                :soul       soul
                                                                :transcript transcript
                                                                :tools      tools})
                         executed-tools    (atom [])
                         recording-tool-fn (when tools
                                             (fn [name arguments]
                                               (let [tc         {:id        (str (java.util.UUID/randomUUID))
                                                                 :name      name
                                                                 :arguments arguments
                                                                 :type      "toolCall"}
                                                     tool-state (atom :pending)
                                                     cancel!    #(when (compare-and-set! tool-state :pending :cancelled)
                                                                   (channel/on-tool-cancel channel key-str tc))]
                                                 (channel/on-tool-call channel key-str tc)
                                                 (bridge/on-cancel! key-str cancel!)
                                                    (let [result ((tool-registry/tool-fn allowed-tools) name (assoc arguments
                                                                                                                     :session-key key-str
                                                                                                                     :state-dir sdir))]
                                                   (when (= :cancelled (:error result))
                                                     (cancel!)
                                                     (throw (ex-info "cancelled" {:type :cancelled})))
                                                   (when (compare-and-set! tool-state :pending :completed)
                                                     (swap! executed-tools conj [tc result])
                                                     (channel/on-tool-result channel key-str tc result))
                                                   result))))
                         _                 (when-let [done (:compaction-llm-done (active-compaction-state key-str))]
                                             (deref done 5000 nil))
                         result            (stream-and-handle-tools! channel key-str provider provider-cfg' request recording-tool-fn)]
                     (if (or (= :cancelled (:error result))
                             (bridge/cancelled-response? result)
                             (bridge/cancelled? key-str))
                       (finish-turn (bridge/cancelled-result))
                       (do
                         (when-not (:error result)
                           (logging/log-stream-completed! key-str))
                         (when (seq @executed-tools)
                           (run-tool-calls! sdir key-str @executed-tools))
                         (let [response-result (process-response! sdir key-str result {:model model :provider provider})
                               final-result    (or response-result result)]
                           (finish-turn final-result)))))))))))
      (catch clojure.lang.ExceptionInfo e
        (if (= :cancelled (:type (ex-data e)))
          (finish-turn (bridge/cancelled-result))
          (throw e)))
      (catch Exception e
        (if (bridge/cancelled? key-str)
          (finish-turn (bridge/cancelled-result))
          (throw e)))
      (finally
        (bridge/end-turn! key-str turn)))))

;; endregion ^^^^^ Public API ^^^^^
