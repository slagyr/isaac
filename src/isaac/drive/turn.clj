(ns isaac.drive.turn
  (:require
    [clojure.string :as str]
    [isaac.bridge.cancellation :as bridge]
    [isaac.comm :as comm]
    [isaac.comm.cli :as cli-comm]
    [isaac.config.loader :as config]
    [isaac.drive.dispatch :as dispatch]
    [isaac.llm.api :as api]
    [isaac.llm.tool-loop :as tool-loop]
    [isaac.logger :as log]
    [isaac.session.compaction :as compaction]
    [isaac.session.context :as session-ctx]
    [isaac.session.logging :as logging]
    [isaac.session.store :as store]
    [isaac.session.store.file :as file-store]
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

(defn- session-store [state-dir]
  (file-store/create-store state-dir))

(defn clear-async-compactions! []
  (reset! in-flight-compactions {}))

(defn- active-compaction-state [session-key]
  (get @in-flight-compactions session-key))

(defn async-compaction-in-flight? [session-key]
  (boolean (active-compaction-state session-key)))

(defn await-async-compaction! [session-key]
  (when-let [state (get @in-flight-compactions session-key)]
    (when-let [splice-ready (:splice-ready state)]
      (deliver splice-ready true))
    (let [future* (:future state)
          result  (deref future* 30000 ::timeout)]
      (when (= ::timeout result)
        (throw (ex-info "async compaction did not complete within 30 seconds" {:session session-key})))
      (swap! in-flight-compactions dissoc session-key)
      result)))

(defn- with-transcript-lock [session-key f]
  (if-let [lock (:lock (active-compaction-state session-key))]
    (locking lock (f))
    (f)))

(defn- append-message! [state-dir session-key message]
  (with-transcript-lock session-key #(store/append-message! (session-store state-dir) session-key message)))

(defn- append-error! [state-dir session-key error-entry]
  (with-transcript-lock session-key #(store/append-error! (session-store state-dir) session-key error-entry)))

(defn run-tool-calls! [state-dir session-key tool-results]
  (doseq [[tc result] tool-results]
    (append-message! state-dir session-key
                     {:role    "assistant"
                      :content [{:type      "toolCall"
                                 :id        (:id tc)
                                 :name      (:name tc)
                                 :arguments (:arguments tc)}]})
    (let [error? (str/starts-with? (str result) "Error:")]
      (append-message! state-dir session-key
                       (cond-> {:role "toolResult" :id (:id tc) :content result}
                               error? (assoc :isError true))))))

(defn- normalized-error [err]
  (if (string? err) (keyword err) err))

(defn- persisted-error [err]
  (let [normalized (normalized-error err)]
    (if (keyword? normalized) (str normalized) normalized)))

(defn- store-error! [state-dir session-key result {:keys [model provider]}]
  (try
    (append-error! state-dir session-key
                   {:content  (error-message result)
                    :error    (persisted-error (:error result))
                    :model    model
                    :provider provider})
    (catch Exception e
      (log/warn :chat/error-not-stored
                :session session-key
                :provider provider
                :error (.getMessage e)))))

(defn- log-response-failed! [session-key provider result]
  (log/error :chat/response-failed
             :session session-key
             :provider provider
             :error (:error result)
             :message (error-message result)))

(defn- report-error! [state-dir session-key provider result opts]
  (log-response-failed! session-key provider result)
  (store-error! state-dir session-key result opts)
  result)

(defn- response-model [result model]
  (or (get-in result [:response :model]) model))

(defn- store-response! [state-dir session-key result {:keys [model provider]}]
  (let [session-store  (session-store state-dir)
        tokens         (extract-tokens result)
        total-tokens   (+ (:input-tokens tokens 0) (:output-tokens tokens 0))
        resolved-model (response-model result model)
        raw-usage      (or (get-in result [:response :response :usage])
                           (get-in result [:response :usage]))
        reasoning      (or (get-in result [:response :reasoning])
                           (get-in result [:response :response :reasoning]))
        session-entry  (or (store/get-session session-store session-key) {})
        input-tokens   (:input-tokens tokens 0)
        output-tokens  (:output-tokens tokens 0)
        cache-read     (:cache-read tokens)
        cache-write    (:cache-write tokens)]
    (logging/log-message-stored! session-key resolved-model tokens)
    (append-message! state-dir session-key
                     (cond-> {:role     "assistant"
                                :content  (or (:content result)
                                              (get-in result [:response :message :content]))
                                :model    resolved-model
                                :provider provider
                                :tokens   total-tokens}
                        raw-usage  (assoc :usage raw-usage)
                        reasoning  (assoc :reasoning reasoning)))
    (store/update-session! session-store session-key
                           (cond-> {:input-tokens      (+ (or (:input-tokens session-entry) 0) input-tokens)
                                    :last-input-tokens input-tokens
                                    :output-tokens     (+ (or (:output-tokens session-entry) 0) output-tokens)
                                    :total-tokens      (+ (+ (or (:input-tokens session-entry) 0) input-tokens)
                                                          (+ (or (:output-tokens session-entry) 0) output-tokens))}
                             cache-read  (assoc :cache-read (+ (or (:cache-read session-entry) 0) cache-read))
                             cache-write (assoc :cache-write (+ (or (:cache-write session-entry) 0) cache-write))))))

(defn process-response! [state-dir session-key result {:keys [model provider]}]
  (if (:error result)
    (report-error! state-dir session-key provider result {:model model :provider provider})
    (store-response! state-dir session-key result {:model model :provider provider})))

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
    (and (:tools request) (stream-supports-tool-calls? (api/config p)))
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

(defn- session-entry [state-dir session-key]
  (store/get-session (session-store state-dir) session-key))

(def ^:private max-compaction-attempts 5)

(defn- consecutive-compaction-failures [entry]
  (or (get-in entry [:compaction :consecutive-failures]) 0))

(defn- reserve-async-compaction! [session-key]
  (let [lock     (Object.)
        claimed? (atom false)]
    (swap! in-flight-compactions
           (fn [state]
             (if (contains? state session-key)
               state
               (do
                 (reset! claimed? true)
                 (assoc state session-key {:lock lock})))))
    (when @claimed? lock)))

(declare run-compaction-check!)

(defn- perform-compaction! [state-dir session-key attempt prompt-tokens {:keys [compaction-llm-done context-window model provider soul splice-ready transcript-lock] ch :comm}]
  (let [provider-name (api/display-name provider)]
    (cond
      (> attempt max-compaction-attempts)
      (log/warn :session/compaction-stopped
                :session session-key
                :provider provider-name
                :model model
                :reason :max-attempts
                :attempt attempt
                :total-tokens prompt-tokens
                :context-window context-window)

      :else
      (do
        (let [started-at (System/currentTimeMillis)]
          (logging/log-compaction-started! session-key provider-name model prompt-tokens context-window)
          (when ch
             (comm/on-compaction-start ch session-key {:provider       provider-name
                                                   :model          model
                                                   :total-tokens   prompt-tokens
                                                   :context-window context-window}))
          (let [result (compaction/compact! state-dir session-key
                                     {:model               model
                                      :api                 provider
                                      :soul                soul
                                      :context-window      context-window
                                      :transcript-lock     transcript-lock
                                      :compaction-llm-done compaction-llm-done
                                      :splice-ready        splice-ready
                                      :chat-fn             (partial dispatch/dispatch-chat-with-tools provider)})]
            (if (:error result)
              (let [failures (inc (consecutive-compaction-failures (session-entry state-dir session-key)))]
                (store/update-session! (session-store state-dir) session-key {:compaction {:consecutive-failures failures}})
                (when ch
                  (comm/on-compaction-failure ch session-key {:consecutive-failures failures
                                                          :error                (:error result)
                                                          :message              (:message result)}))
                (when (>= failures max-compaction-attempts)
                  (store/update-session! (session-store state-dir) session-key {:compaction-disabled true})
                  (when ch
                    (comm/on-compaction-disabled ch session-key {:reason :too-many-failures}))
                  (log/warn :session/compaction-stopped
                            :session session-key
                            :provider provider-name
                            :model model
                            :reason :too-many-failures
                            :attempt attempt
                            :total-tokens prompt-tokens
                            :context-window context-window))
                (log/error :session/compaction-failed
                           :session session-key
                           :provider provider-name
                           :model model
                           :error (:error result)
                           :message (:message result)))
              (do
                (store/update-session! (session-store state-dir) session-key {:compaction-disabled false
                                                                               :compaction          {:consecutive-failures 0}})
                (when ch
                  (comm/on-compaction-success ch session-key {:summary      (:summary result)
                                                          :tokens-saved (max 0 (- prompt-tokens (:last-input-tokens (session-entry state-dir session-key) 0)))
                                                          :duration-ms  (- (System/currentTimeMillis) started-at)}))
                (when-not (:chunked result)
                  (let [updated-total (:last-input-tokens (session-entry state-dir session-key) 0)]
                    (if (>= updated-total prompt-tokens)
                      (log/warn :session/compaction-stopped
                                :session session-key
                                :provider provider-name
                                :model model
                                :reason :no-progress
                                :attempt attempt
                                :total-tokens updated-total
                                :context-window context-window)
                      (run-compaction-check! state-dir session-key
                                             {:comm            ch
                                              :context-window  context-window
                                              :model           model
                                              :provider        provider
                                              :soul            soul
                                              :transcript-lock transcript-lock}
                                             (inc attempt)
                                             false))))))))))))

(defn- start-async-compaction! [state-dir session-key opts]
  (when-let [lock (reserve-async-compaction! session-key)]
    (let [compaction-llm-done (promise)
          splice-ready        (promise)
          task                (bound-fn []
                                (run-compaction-check! state-dir session-key
                                                       (assoc opts
                                                         :transcript-lock lock
                                                         :compaction-llm-done compaction-llm-done
                                                         :splice-ready splice-ready)
                                                       1 false))
          future*             (future (task))]
      (swap! in-flight-compactions assoc session-key {:future              future*
                                                  :lock                lock
                                                  :compaction-llm-done compaction-llm-done
                                                  :splice-ready        splice-ready})
      future*)))

(defn- run-compaction-check! [state-dir session-key {:keys [context-window model provider] :as opts} attempt allow-async?]
  (let [entry        (session-entry state-dir session-key)
        _failures    (consecutive-compaction-failures entry)
        total-tokens (:last-input-tokens entry 0)
        config       (compaction/resolve-config entry context-window)
        prov-name    (when provider (api/display-name provider))]
    (logging/log-compaction-check! session-key prov-name model total-tokens context-window)
    (cond
      (:compaction-disabled entry)
      (logging/log-compaction-skipped! session-key prov-name model total-tokens context-window :disabled)

      (compaction/should-compact? entry context-window)
      (if (and allow-async? (:async? config))
        (start-async-compaction! state-dir session-key opts)
        (perform-compaction! state-dir session-key attempt total-tokens opts)))))

(defn check-compaction! [state-dir session-key opts]
  (run-compaction-check! state-dir session-key opts 1 true))

;; endregion ^^^^^ Context Compaction ^^^^^

;; region ----- Request Building -----

(defn- tool-capable-provider? [p]
  (not (contains? #{"claude-sdk"} (api/api-of p))))

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
    (builtin/register-all!)))

(defn build-chat-request [p {:keys [boot-files model soul transcript tools]}]
  (let [prompt-out (api/build-prompt p {:boot-files boot-files :model model :soul soul
                                        :transcript transcript :tools tools})]
    (cond-> {:model (:model prompt-out) :messages (:messages prompt-out)}
            (:system prompt-out) (assoc :system (:system prompt-out))
            (:max_tokens prompt-out) (assoc :max_tokens (:max_tokens prompt-out))
            (:tools prompt-out) (assoc :tools (:tools prompt-out)))))

;; endregion ^^^^^ Request Building ^^^^^

;; region ----- Public API -----

(defn- augment-provider
  "Wrap an upstream Api with per-turn runtime values (state-dir,
   session-key, context-window) merged into its config. Returns a new
   Api instance — the upstream one is unchanged."
  [p state-dir session-key context-window]
  (when p
    (let [cfg (merge (or (api/config p) {})
                     {:state-dir      state-dir
                      :session-key    session-key
                      :context-window context-window})]
      (dispatch/make-provider (api/display-name p) cfg))))

(defn- build-turn-ctx [state-dir session-key opts]
  (let [{:keys [context-window model module-index provider soul]} opts
        ch (get opts :comm cli-comm/channel)
        cfg            (or (config/snapshot) {})
        crew-members   (or (:crew cfg) {})
        session        (store/get-session (session-store state-dir) session-key)
        crew-id        (or (:crew session) "main")
        validate-crew? (seq crew-members)
        crew-known?    (or (not validate-crew?)
                           (contains? crew-members crew-id))
        turn-ctx       (when crew-known?
                         (session-ctx/resolve-turn-context {:cfg  cfg
                                                            :cwd  (:cwd session)
                                                            :home state-dir}
                                                           crew-id))]
    {:comm           ch
     :crew           crew-id
     :crew-known?    crew-known?
     :boot-files     (:boot-files turn-ctx)
     :context-window context-window
     :model          model
     :module-index   (or module-index
                         (some-> provider api/config :module-index))
     :provider       (when crew-known? (augment-provider provider state-dir session-key context-window))
     :allowed-tools  (allowed-tool-names crew-members crew-id)
     :soul           soul}))

(defn- finish-turn! [ch session-key result]
  (comm/on-turn-end ch session-key result)
  result)

(defn- reject-unknown-crew! [ch session-key crew-id]
  (let [message (str "unknown crew: " crew-id "\n"
                     "use /crew {name} to switch, or add " crew-id " to config\n")]
    (logging/log-turn-rejected! session-key crew-id :unknown-crew)
    (comm/on-text-chunk ch session-key message)
    {:error :unknown-crew :already-emitted? true :message message}))

(defn- record-tool-call!
  "Wrap a tool invocation with comm callbacks, cancellation tracking, and
   accumulation into the executed-tools atom for later transcript persistence."
  [{:keys [session-key state-dir allowed-tools module-index executed-tools] ch :comm} name arguments]
  (let [tc         {:id (str (java.util.UUID/randomUUID)) :name name :arguments arguments :type "toolCall"}
        tool-state (atom :pending)
        cancel!    #(when (compare-and-set! tool-state :pending :cancelled)
                      (comm/on-tool-cancel ch session-key tc))]
    (comm/on-tool-call ch session-key tc)
    (bridge/on-cancel! session-key cancel!)
    (let [tool-fn* (if module-index
                     (tool-registry/tool-fn allowed-tools module-index)
                     (tool-registry/tool-fn allowed-tools))
          result   (tool-fn*
                     name
                     (assoc arguments "session_key" session-key "state_dir" state-dir))]
      (when (= :cancelled (:error result))
        (cancel!)
        (throw (ex-info "cancelled" {:type :cancelled})))
      (when (compare-and-set! tool-state :pending :completed)
        (swap! executed-tools conj [tc result])
        (comm/on-tool-result ch session-key tc result))
      result)))

(defn- execute-llm-turn!
  "Build the chat request, drive the tool-loop, persist tool pairs and the
   final assistant response. Returns the final result map."
  [state-dir session-key input ctx]
  (let [{:keys [provider allowed-tools model module-index boot-files soul]} ctx
        ch (get ctx :comm)
        p provider]
    (append-message! state-dir session-key {:role "user" :content input})
    (let [transcript      (with-transcript-lock session-key #(store/get-transcript (session-store state-dir) session-key))
          tools           (active-tools p allowed-tools module-index)
          request         (build-chat-request p {:boot-files boot-files
                                                 :model      model
                                                 :soul       soul
                                                 :transcript transcript
                                                 :tools      tools})
          current-request (atom request)
          executed-tools  (atom [])
          tool-fn         (partial record-tool-call! {:comm           ch
                                                      :session-key        session-key
                                                      :state-dir      state-dir
                                                      :allowed-tools  allowed-tools
                                                      :module-index   module-index
                                                      :executed-tools executed-tools})]
      (when-let [done (:compaction-llm-done (active-compaction-state session-key))]
        (deref done 5000 nil))
      (let [chat-fn     (chat-fn-for ch session-key p request)
            followup-fn (fn [req response tool-calls tool-results]
                          (let [messages (api/followup-messages p req response tool-calls tool-results)]
                            (reset! current-request (assoc req :messages messages))
                            messages))
            result      (-> (tool-loop/run chat-fn followup-fn request tool-fn)
                            (final-loop-summary chat-fn @current-request)
                            canned-loop-exhausted-message)]
        (cond
          (or (= :cancelled (:error result))
              (bridge/cancelled-response? result)
              (bridge/cancelled? session-key))
          (bridge/cancelled-result)

          :else
          (do
            (when-not (:error result)
              (logging/log-stream-completed! session-key))
            (when (seq @executed-tools)
              (run-tool-calls! state-dir session-key @executed-tools))
            (or (process-response! state-dir session-key result {:model model :provider (api/display-name p)})
                result)))))))

(defn- run-turn-body!
  "The successful-path pipeline. Returns the result that finish-turn! should
   wrap. Each branch is a single call into a focused helper."
  [state-dir session-key input ctx]
  (cond
    (bridge/cancelled? session-key)
    (bridge/cancelled-result)

    (not (:crew-known? ctx))
    (reject-unknown-crew! (:comm ctx) session-key (:crew ctx))

    :else
    (do
      (logging/log-turn-accepted! session-key (:crew ctx))
      (check-compaction! state-dir session-key {:boot-files     (:boot-files ctx)
                                            :model          (:model ctx)
                                            :soul           (:soul ctx)
                                            :context-window (:context-window ctx)
                                            :provider       (:provider ctx)
                                            :comm           (:comm ctx)})
      (if (bridge/cancelled? session-key)
        (bridge/cancelled-result)
        (execute-llm-turn! state-dir session-key input ctx)))))

(defn- record-exception! [state-dir session-key e {:keys [model provider]}]
  (append-error! state-dir session-key {:content  (.getMessage e)
                                    :error    "exception"
                                    :ex-class (.getName (class e))
                                    :model    model
                                    :provider (when provider (api/display-name provider))}))

(defn run-turn!
  [state-dir session-key input opts]
  (let [ctx     (build-turn-ctx state-dir session-key opts)
        ch      (:comm ctx)
        turn    (bridge/begin-turn! session-key)
        finish! #(finish-turn! ch session-key %)]
    (try
      (comm/on-turn-start ch session-key input)
      (ensure-default-tools-registered!)
      (finish! (run-turn-body! state-dir session-key input ctx))
      (catch ExceptionInfo e
        (if (= :cancelled (:type (ex-data e)))
          (finish! (bridge/cancelled-result))
          (do (record-exception! state-dir session-key e ctx) (throw e))))
      (catch Exception e
        (if (bridge/cancelled? session-key)
          (finish! (bridge/cancelled-result))
          (do (record-exception! state-dir session-key e ctx) (throw e))))
      (finally
        (bridge/end-turn! session-key turn)))))

;; endregion ^^^^^ Public API ^^^^^
