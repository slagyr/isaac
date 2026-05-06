(ns isaac.drive.turn
  (:require
    [clojure.string :as str]
    [isaac.bridge :as bridge]
    [isaac.comm :as comm]
    [isaac.comm.cli :as cli-comm]
    [isaac.context.manager :as ctx]
    [isaac.drive.dispatch :as dispatch]
    [isaac.llm.tool-loop :as tool-loop]
    [isaac.logger :as log]
    [isaac.prompt.anthropic :as anthropic-prompt]
    [isaac.prompt.builder :as prompt]
    [isaac.provider :as provider]
    [isaac.session.compaction :as compaction]
    [isaac.session.context :as session-ctx]
    [isaac.session.logging :as logging]
    [isaac.session.storage :as storage]
    [isaac.tool.builtin :as builtin]
    [isaac.tool.registry :as tool-registry])
  (:import (clojure.lang ExceptionInfo)))

;; region ----- Error Formatting -----

(defn- body-error-message [result]
  (let [body       (:body result)
        body-error (:error body)]
    (cond
      (map? body-error) (str (or (:type body-error) (name (:error result)))
                             ": "
                             (or (:message body-error) body-error))
      (string? body-error) body-error
      (map? body) (pr-str body))))

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
    {:input-tokens  (or (:input-tokens usage) (:prompt_eval_count resp) 0)
     :output-tokens (or (:output-tokens usage) (:eval_count resp) 0)
     :cache-read     (:cache-read usage)
     :cache-write    (:cache-write usage)}))

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

(defn- append-message! [state-dir key-str message]
  (with-transcript-lock key-str #(storage/append-message! state-dir key-str message)))

(defn- append-error! [state-dir key-str error-entry]
  (with-transcript-lock key-str #(storage/append-error! state-dir key-str error-entry)))

(defn run-tool-calls! [state-dir key-str tool-results]
  (doseq [[tc result] tool-results]
    (append-message! state-dir key-str
                     {:role    "assistant"
                      :content [{:type      "toolCall"
                                 :id        (:id tc)
                                 :name      (:name tc)
                                 :arguments (:arguments tc)}]})
    (let [error? (str/starts-with? (str result) "Error:")]
      (append-message! state-dir key-str
                       (cond-> {:role "toolResult" :id (:id tc) :content result}
                               error? (assoc :isError true))))))

(defn- normalized-error [err]
  (if (string? err) (keyword err) err))

(defn- persisted-error [err]
  (let [normalized (normalized-error err)]
    (if (keyword? normalized) (str normalized) normalized)))

(defn- store-error! [state-dir key-str result {:keys [model provider]}]
  (try
    (append-error! state-dir key-str
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

(defn- report-error! [state-dir key-str provider result opts]
  (log-response-failed! key-str provider result)
  (store-error! state-dir key-str result opts)
  result)

(defn- response-model [result model]
  (or (get-in result [:response :model]) model))

(defn- store-response! [state-dir key-str result {:keys [model provider]}]
  (let [tokens         (extract-tokens result)
        total-tokens   (+ (:input-tokens tokens 0) (:output-tokens tokens 0))
        resolved-model (response-model result model)
        raw-usage      (or (get-in result [:response :response :usage])
                           (get-in result [:response :usage]))
        reasoning      (or (get-in result [:response :reasoning])
                           (get-in result [:response :response :reasoning]))]
    (logging/log-message-stored! key-str resolved-model tokens)
    (append-message! state-dir key-str
                     (cond-> {:role     "assistant"
                                :content  (or (:content result)
                                              (get-in result [:response :message :content]))
                                :model    resolved-model
                                :provider provider
                                :tokens   total-tokens}
                        raw-usage  (assoc :usage raw-usage)
                        reasoning  (assoc :reasoning reasoning)))
    (storage/update-tokens! state-dir key-str tokens)))

(defn process-response! [state-dir key-str result {:keys [model provider]}]
  (if (:error result)
    (report-error! state-dir key-str provider result {:model model :provider provider})
    (store-response! state-dir key-str result {:model model :provider provider})))

;; endregion ^^^^^ Response Persistence ^^^^^

;; region ----- Streaming -----

(defn- chunk-content [chunk]
  (let [content (or (get-in chunk [:message :content])
                    (get-in chunk [:delta :text])
                    (get-in chunk [:choices 0 :delta :content]))]
    (cond
      (string? content) content
      (vector? content) (apply str content)
      (nil? content) nil
      :else (str content))))

(defn- chunk-piece [full-content chunk]
  (when-let [content (chunk-content chunk)]
    (if (and (:done chunk)
             (seq full-content)
             (str/starts-with? content full-content))
      (subs content (count full-content))
      content)))

(defn stream-response! [p request on-chunk]
  (let [full-content (atom "")
        final-resp   (atom nil)
        result       (dispatch/dispatch-chat-stream p request
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

(defn print-streaming-response [p request]
  (let [result (stream-response! p request
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
                  (nil? content) []
                  :else [(str content)])]
    (doseq [chunk chunks]
      (comm/on-text-chunk channel-impl session-key chunk))
    (apply str chunks)))

(defn- stream-supports-tool-calls? [provider-config]
  (let [raw (or (get provider-config :streamSupportsToolCalls)
                (get provider-config :stream-supports-tool-calls))]
    (cond
      (nil? raw) true
      (boolean? raw) raw
      (string? raw) (not (#{"false" "0" "no" "off"} (str/lower-case raw)))
      :else (boolean raw))))

(defn- unwrap-stream-result
  "stream-response! returns {:content streamed-text :response chat-response}.
   The tool loop wants the inner chat-response so it can read :message and :tool-calls."
  [result]
  (cond
    (:error result) result
    (:response result) (:response result)
    :else result))

(defn- chat-fn-for
  "Pick the LLM-call hook the tool-loop should use this turn.

   - Tools requested, streaming supports tools: stream deltas via Comm callbacks.
   - Otherwise (no tools, or tools but streaming not supported): one-shot chat,
     emit content as a single Comm chunk."
  [channel-impl session-key p request]
  (cond
    (and (:tools request) (stream-supports-tool-calls? (provider/config p)))
    (fn [req] (unwrap-stream-result
                (stream-response! p req
                                  (fn [chunk] (comm/on-text-chunk channel-impl session-key chunk)))))

    :else
    (fn [req] (let [result (dispatch/dispatch-chat p req)]
                (if (:error result)
                  result
                  (let [joined (emit-response-content! channel-impl session-key result)]
                    (assoc-in result [:message :content] joined)))))))

(defn- canned-loop-exhausted-message [result]
  (let [content-blank? (str/blank? (or (:content result)
                                       (get-in result [:response :message :content])
                                       (get-in result [:response :content])))]
    (if (and (:loop-request? result) content-blank?)
      (let [message "I ran several tools but did not reach a conclusion before hitting the tool loop limit. Ask me to continue if you want me to keep digging."]
        (-> result
            (assoc :content message)
            (assoc-in [:response :message :content] message)))
      result)))

(def ^:private loop-exhausted-summary-instruction
  "You have hit the tool loop limit. Do not call any more tools. Write a concise assistant reply for the user using what you learned so far. If you still cannot fully answer, summarize the useful findings and what remains unresolved.")

(defn- loop-summary-request [request response]
  (let [assistant-msg (or (:message response)
                          {:role "assistant"
                           :content (or (:content response) "")})]
    (-> request
        (assoc :messages (conj (vec (:messages request))
                               assistant-msg
                               {:role "user" :content loop-exhausted-summary-instruction}))
        (assoc :tools []))))

(defn- merge-response-tokens [token-counts response]
  (let [usage (:usage response)]
    (merge-with + token-counts
                {:input-tokens  (or (:input-tokens usage) (:prompt_eval_count response) 0)
                 :output-tokens (or (:output-tokens usage) (:eval_count response) 0)
                 :cache-read    (or (:cache-read usage) 0)
                 :cache-write   (or (:cache-write usage) 0)})))

(defn- final-loop-summary [result chat-fn current-request]
  (let [content (or (:content result)
                    (get-in result [:response :message :content])
                    (get-in result [:response :content]))]
    (if (or (not (:loop-request? result))
            (not (str/blank? content)))
      result
      (let [summary-response (chat-fn (loop-summary-request current-request (:response result)))
            summary-content  (get-in summary-response [:message :content])]
        (if (or (:error summary-response)
                (str/blank? summary-content))
          result
          (-> result
              (assoc :content summary-content)
              (assoc :response summary-response)
              (assoc :token-counts (merge-response-tokens (:token-counts result) summary-response))))))))

;; endregion ^^^^^ Streaming ^^^^^

;; region ----- Context Compaction -----

(defn- session-entry [state-dir key-str]
  (storage/get-session state-dir key-str))

(def ^:private max-compaction-attempts 5)

(defn- consecutive-compaction-failures [entry]
  (or (get-in entry [:compaction :consecutive-failures]) 0))

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

(defn- perform-compaction! [state-dir key-str attempt prompt-tokens {:keys [channel compaction-llm-done context-window model provider soul splice-ready transcript-lock]}]
  (let [provider-name (provider/display-name provider)]
    (cond
      (> attempt max-compaction-attempts)
      (log/warn :session/compaction-stopped
                :session key-str
                :provider provider-name
                :model model
                :reason :max-attempts
                :attempt attempt
                :total-tokens prompt-tokens
                :context-window context-window)

      :else
      (do
        (let [started-at (System/currentTimeMillis)]
          (logging/log-compaction-started! key-str provider-name model prompt-tokens context-window)
          (when channel
             (comm/on-compaction-start channel key-str {:provider       provider-name
                                                        :model          model
                                                        :total-tokens   prompt-tokens
                                                        :context-window context-window}))
          (let [result (ctx/compact! state-dir key-str
                                     {:model               model
                                      :provider            provider
                                      :soul                soul
                                      :context-window      context-window
                                      :transcript-lock     transcript-lock
                                      :compaction-llm-done compaction-llm-done
                                      :splice-ready        splice-ready
                                      :chat-fn             (partial dispatch/dispatch-chat-with-tools provider)})]
            (if (:error result)
              (let [failures (inc (consecutive-compaction-failures (session-entry state-dir key-str)))]
                (storage/update-session! state-dir key-str {:compaction {:consecutive-failures failures}})
                (when channel
                  (comm/on-compaction-failure channel key-str {:consecutive-failures failures
                                                               :error                (:error result)
                                                               :message              (:message result)}))
                (when (>= failures max-compaction-attempts)
                  (storage/update-session! state-dir key-str {:compaction-disabled true})
                  (when channel
                    (comm/on-compaction-disabled channel key-str {:reason :too-many-failures}))
                  (log/warn :session/compaction-stopped
                            :session key-str
                            :provider provider-name
                            :model model
                            :reason :too-many-failures
                            :attempt attempt
                            :total-tokens prompt-tokens
                            :context-window context-window))
                (log/error :session/compaction-failed
                           :session key-str
                           :provider provider-name
                           :model model
                           :error (:error result)
                           :message (:message result)))
              (do
                (storage/update-session! state-dir key-str {:compaction-disabled false
                                                            :compaction          {:consecutive-failures 0}})
                (when channel
                  (comm/on-compaction-success channel key-str {:summary      (:summary result)
                                                               :tokens-saved (max 0 (- prompt-tokens (:last-input-tokens (session-entry state-dir key-str) 0)))
                                                               :duration-ms  (- (System/currentTimeMillis) started-at)}))
                (when-not (:chunked result)
                  (let [updated-total (:last-input-tokens (session-entry state-dir key-str) 0)]
                    (if (>= updated-total prompt-tokens)
                      (log/warn :session/compaction-stopped
                                :session key-str
                                :provider provider-name
                                :model model
                                :reason :no-progress
                                :attempt attempt
                                :total-tokens updated-total
                                :context-window context-window)
                      (run-compaction-check! state-dir key-str
                                             {:channel         channel
                                              :context-window  context-window
                                              :model           model
                                              :provider        provider
                                              :soul            soul
                                              :transcript-lock transcript-lock}
                                             (inc attempt)
                                             false))))))))))))

(defn- start-async-compaction! [state-dir key-str opts]
  (when-let [lock (reserve-async-compaction! key-str)]
    (let [compaction-llm-done (promise)
          splice-ready        (promise)
          future*             (future
                                (run-compaction-check! state-dir key-str
                                                       (assoc opts
                                                         :transcript-lock lock
                                                         :compaction-llm-done compaction-llm-done
                                                         :splice-ready splice-ready)
                                                       1 false))]
      (swap! in-flight-compactions assoc key-str {:future              future*
                                                  :lock                lock
                                                  :compaction-llm-done compaction-llm-done
                                                  :splice-ready        splice-ready})
      future*)))

(defn- run-compaction-check! [state-dir key-str {:keys [context-window model provider] :as opts} attempt allow-async?]
  (let [entry        (session-entry state-dir key-str)
         _failures    (consecutive-compaction-failures entry)
         total-tokens (:last-input-tokens entry 0)
         config       (compaction/resolve-config entry context-window)
         prov-name    (when provider (provider/display-name provider))]
    (logging/log-compaction-check! key-str prov-name model total-tokens context-window)
    (cond
      (:compaction-disabled entry)
      (logging/log-compaction-skipped! key-str prov-name model total-tokens context-window :disabled)

      (ctx/should-compact? entry context-window)
      (if (and allow-async? (:async? config))
        (start-async-compaction! state-dir key-str opts)
        (perform-compaction! state-dir key-str attempt total-tokens opts)))))

(defn check-compaction! [state-dir key-str opts]
  (run-compaction-check! state-dir key-str opts 1 true))

;; endregion ^^^^^ Context Compaction ^^^^^

;; region ----- Request Building -----

(defn- tool-capable-provider? [p]
  (not (contains? #{"claude-sdk"} (provider/api-of p))))

(defn- allowed-tool-names [crew-members crew-id]
  (when-let [crew (get crew-members crew-id)]
    (when (contains? crew :tools)
      (->> (get-in crew [:tools :allow])
           (mapv (fn [tool]
                   (cond
                     (keyword? tool) (name tool)
                     (string? tool) tool
                     :else (str tool))))
           set))))

(defn- active-tools [p allowed-tools module-index]
  (when (tool-capable-provider? p)
    (not-empty (if module-index
                 (tool-registry/tool-definitions allowed-tools module-index)
                 (tool-registry/tool-definitions allowed-tools)))))

(defn- ensure-default-tools-registered! []
  (when (empty? (tool-registry/all-tools))
    (builtin/register-all! tool-registry/register!)))

(defn build-chat-request [p {:keys [boot-files model soul transcript tools]}]
  (let [build-fn   (if (= "anthropic-messages" (provider/api-of p))
                     anthropic-prompt/build
                     prompt/build)
        prompt-out (build-fn {:boot-files boot-files :model model :soul soul
                              :transcript transcript :tools tools
                              :provider   (provider/display-name p)})]
    (cond-> {:model (:model prompt-out) :messages (:messages prompt-out)}
            (:system prompt-out) (assoc :system (:system prompt-out))
            (:max_tokens prompt-out) (assoc :max_tokens (:max_tokens prompt-out))
            (:tools prompt-out) (assoc :tools (:tools prompt-out)))))

;; endregion ^^^^^ Request Building ^^^^^

;; region ----- Public API -----

(defn- augment-provider
  "Wrap an upstream Provider with per-turn runtime values (state-dir,
   session-key, context-window) merged into its config. Returns a new
   Provider instance — the upstream one is unchanged."
  [p state-dir key-str context-window]
  (when p
    (let [cfg (merge (or (provider/config p) {})
                     {:state-dir state-dir
                      :session-key key-str
                      :context-window context-window})]
      (dispatch/make-provider (provider/display-name p) cfg))))

(defn- build-turn-ctx [state-dir key-str opts]
  (let [{:keys [channel context-window crew-members model models module-index provider soul]
         :or   {channel cli-comm/channel}} opts
        session        (storage/get-session state-dir key-str)
        crew-id        (or (:crew session) "main")
        validate-crew? (seq crew-members)
        crew-known?    (or (not validate-crew?)
                           (contains? crew-members crew-id))
        turn-ctx       (when crew-known?
                         (session-ctx/resolve-turn-context {:crew-members crew-members
                                                            :models       models
                                                            :cwd          (:cwd session)
                                                            :home         state-dir}
                                                           crew-id))]
    {:channel        channel
     :crew           crew-id
     :crew-known?    crew-known?
     :crew-members   crew-members
     :boot-files     (:boot-files turn-ctx)
     :context-window context-window
     :model          model
      :module-index   (or module-index
                          (some-> provider provider/config :module-index))
      :models         models
      :provider       (when crew-known? (augment-provider provider state-dir key-str context-window))
      :allowed-tools  (allowed-tool-names crew-members crew-id)
      :soul           soul}))

(defn- finish-turn! [channel key-str result]
  (comm/on-turn-end channel key-str result)
  result)

(defn- reject-unknown-crew! [channel key-str crew-id]
  (let [message (str "unknown crew: " crew-id "\n"
                     "use /crew {name} to switch, or add " crew-id " to config\n")]
    (logging/log-turn-rejected! key-str crew-id :unknown-crew)
    (comm/on-text-chunk channel key-str message)
    {:error :unknown-crew :already-emitted? true :message message}))

(defn- record-tool-call!
  "Wrap a tool invocation with comm callbacks, cancellation tracking, and
   accumulation into the executed-tools atom for later transcript persistence."
  [{:keys [channel key-str state-dir allowed-tools module-index executed-tools]} name arguments]
  (let [tc         {:id (str (java.util.UUID/randomUUID)) :name name :arguments arguments :type "toolCall"}
        tool-state (atom :pending)
        cancel!    #(when (compare-and-set! tool-state :pending :cancelled)
                      (comm/on-tool-cancel channel key-str tc))]
    (comm/on-tool-call channel key-str tc)
    (bridge/on-cancel! key-str cancel!)
    (let [tool-fn* (if module-index
                     (tool-registry/tool-fn allowed-tools module-index)
                     (tool-registry/tool-fn allowed-tools))
          result (tool-fn*
                   name
                   (assoc arguments "session_key" key-str "state_dir" state-dir))]
      (when (= :cancelled (:error result))
        (cancel!)
        (throw (ex-info "cancelled" {:type :cancelled})))
      (when (compare-and-set! tool-state :pending :completed)
        (swap! executed-tools conj [tc result])
        (comm/on-tool-result channel key-str tc result))
      result)))

(defn- execute-llm-turn!
  "Build the chat request, drive the tool-loop, persist tool pairs and the
   final assistant response. Returns the final result map."
  [state-dir key-str input ctx]
  (let [{:keys [channel provider allowed-tools model module-index boot-files soul]} ctx
        p provider]
    (append-message! state-dir key-str {:role "user" :content input})
    (let [transcript     (with-transcript-lock key-str #(storage/get-transcript state-dir key-str))
          tools          (active-tools p allowed-tools module-index)
          request        (build-chat-request p {:boot-files boot-files
                                                :model      model
                                                :soul       soul
                                                :transcript transcript
                                                :tools      tools})
          current-request (atom request)
          executed-tools (atom [])
           tool-fn        (partial record-tool-call! {:channel        channel
                                                      :key-str        key-str
                                                      :state-dir      state-dir
                                                      :allowed-tools  allowed-tools
                                                      :module-index   module-index
                                                      :executed-tools executed-tools})]
      (when-let [done (:compaction-llm-done (active-compaction-state key-str))]
        (deref done 5000 nil))
      (let [chat-fn     (chat-fn-for channel key-str p request)
            followup-fn (fn [req response tool-calls tool-results]
                          (let [messages (provider/followup-messages p req response tool-calls tool-results)]
                            (reset! current-request (assoc req :messages messages))
                            messages))
            result      (-> (tool-loop/run chat-fn followup-fn request tool-fn)
                            (final-loop-summary chat-fn @current-request)
                            canned-loop-exhausted-message)]
        (cond
          (or (= :cancelled (:error result))
              (bridge/cancelled-response? result)
              (bridge/cancelled? key-str))
          (bridge/cancelled-result)

          :else
          (do
            (when-not (:error result)
              (logging/log-stream-completed! key-str))
            (when (seq @executed-tools)
              (run-tool-calls! state-dir key-str @executed-tools))
            (or (process-response! state-dir key-str result {:model model :provider (provider/display-name p)})
                result)))))))

(defn- run-turn-body!
  "The successful-path pipeline. Returns the result that finish-turn! should
   wrap. Each branch is a single call into a focused helper."
  [state-dir key-str input ctx]
  (cond
    (bridge/cancelled? key-str)
    (bridge/cancelled-result)

    (not (:crew-known? ctx))
    (reject-unknown-crew! (:channel ctx) key-str (:crew ctx))

    :else
    (do
      (logging/log-turn-accepted! key-str (:crew ctx))
      (check-compaction! state-dir key-str {:boot-files     (:boot-files ctx)
                                            :model          (:model ctx)
                                            :soul           (:soul ctx)
                                            :context-window (:context-window ctx)
                                            :provider       (:provider ctx)
                                            :channel        (:channel ctx)})
      (if (bridge/cancelled? key-str)
        (bridge/cancelled-result)
        (execute-llm-turn! state-dir key-str input ctx)))))

(defn- record-exception! [state-dir key-str e {:keys [model provider]}]
  (append-error! state-dir key-str {:content  (.getMessage e)
                                    :error    "exception"
                                    :ex-class (.getName (class e))
                                    :model    model
                                    :provider (when provider (provider/display-name provider))}))

(defn run-turn!
  [state-dir key-str input opts]
  (let [ctx     (build-turn-ctx state-dir key-str opts)
        channel (:channel ctx)
        turn    (bridge/begin-turn! key-str)
        finish! #(finish-turn! channel key-str %)]
    (try
      (comm/on-turn-start channel key-str input)
      (ensure-default-tools-registered!)
      (finish! (run-turn-body! state-dir key-str input ctx))
      (catch ExceptionInfo e
        (if (= :cancelled (:type (ex-data e)))
          (finish! (bridge/cancelled-result))
          (do (record-exception! state-dir key-str e ctx) (throw e))))
      (catch Exception e
        (if (bridge/cancelled? key-str)
          (finish! (bridge/cancelled-result))
          (do (record-exception! state-dir key-str e ctx) (throw e))))
      (finally
        (bridge/end-turn! key-str turn)))))

;; endregion ^^^^^ Public API ^^^^^
