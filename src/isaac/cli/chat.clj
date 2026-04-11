;; mutation-tested: 2026-04-08
(ns isaac.cli.chat
  (:require
    [clojure.string :as str]
    [isaac.channel :as channel]
    [isaac.channel.cli :as cli-channel]
    [isaac.cli.registry :as registry]
    [isaac.config.resolution :as config]
    [isaac.context.manager :as ctx]
    [isaac.llm.anthropic :as anthropic]
    [isaac.llm.claude-sdk :as claude-sdk]
    [isaac.llm.grover :as grover]
    [isaac.llm.ollama :as ollama]
    [isaac.llm.openai-compat :as openai-compat]
    [isaac.logger :as log]
    [isaac.prompt.anthropic :as anthropic-prompt]
    [isaac.prompt.builder :as prompt]
    [isaac.session.key :as key]
    [isaac.session.storage :as storage]
    [isaac.tool.builtin :as builtin]
    [isaac.tool.registry :as tool-registry]))

;; region ----- Helpers -----

(defn- username []
  ;; TODO - MDM: use c3kit.apron.env
  (or (System/getenv "USER")
      (System/getenv "LOGNAME")
      "user"))

(defn- provider-base-url [provider]
  (or (:baseUrl provider) "http://localhost:11434"))

(defn- resolved-context-window [provider configured-window]
  (or configured-window (:contextWindow provider) 32768))

(defn- alias-model-info [alias-match provider]
  {:model          (:model alias-match)
   :provider       (:provider alias-match)
   :base-url       (provider-base-url provider)
   :context-window (resolved-context-window provider (:contextWindow alias-match))})

(defn- parsed-model-info [parsed provider]
  {:model          (:model parsed)
   :provider       (:provider parsed)
   :base-url       (provider-base-url provider)
   :context-window (resolved-context-window provider nil)})

(defn- default-model-info [model-ref]
  {:model          model-ref
   :provider       "ollama"
   :base-url       "http://localhost:11434"
   :context-window 32768})

(defn- resolve-model-info [cfg agent-cfg model-override]
  (let [model-ref (or model-override
                      (:model agent-cfg)
                      (get-in cfg [:agents :defaults :model]))
        ;; Check if it's an alias in the agents models map
        agents-models (get-in cfg [:agents :models])
        alias-match   (get agents-models (keyword model-ref))
        ;; Parse as provider/model format
        parsed        (when-not alias-match (config/parse-model-ref model-ref))
        ;; Resolve provider
        provider-name (or (:provider alias-match) (:provider parsed))
        provider      (when provider-name (config/resolve-provider cfg provider-name))]
    (or (when alias-match (alias-model-info alias-match provider))
        (when parsed (parsed-model-info parsed provider))
        (default-model-info model-ref))))

(defn- state-dir [cfg]
  (or (:stateDir cfg) (str (System/getProperty "user.home") "/.isaac")))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Session Management -----

(defn- find-most-recent-session [sdir agent-id]
  (let [sessions (storage/list-sessions sdir agent-id)]
    (->> sessions
         (filter #(str/includes? (:key %) ":cli:"))
         (sort-by :updatedAt)
         last)))

(defn- create-or-resume-session [sdir agent-id {:keys [resume session-key]}]
  (cond
    session-key
    (do (storage/create-session! sdir session-key)
        (println (str "Resuming session: " session-key))
        session-key)

    resume
    (if-let [entry (find-most-recent-session sdir agent-id)]
      (do (println (str "Resuming session: " (:key entry)))
          (let [transcript (storage/get-transcript sdir (:key entry))
                msg-count  (count (filter #(= "message" (:type %)) transcript))]
            (println (str "  " msg-count " messages")))
          (:key entry))
      (do (println "No previous session found, creating new...")
          (let [k (key/build-key {:agent agent-id :channel "cli" :chatType "direct" :conversation (username)})]
            (storage/create-session! sdir k)
            k)))

    :else
    (let [k (key/build-key {:agent agent-id :channel "cli" :chatType "direct" :conversation (username)})]
      (storage/create-session! sdir k)
      k)))

;; endregion ^^^^^ Session Management ^^^^^

;; region ----- Provider Dispatch -----

(defn- resolve-api [provider provider-config]
  (or (:api provider-config)
      (cond
        (= provider "claude-sdk")               "claude-sdk"
        (= provider "grover")                   "grover"
        (str/starts-with? provider "anthropic") "anthropic-messages"
        (= provider "ollama")                   "ollama"
        :else                                   "ollama")))

(defn- ollama-opts [provider-config]
  {:base-url (or (:baseUrl provider-config) "http://localhost:11434")})

(defn- provider-chat [provider provider-config request]
  (case (resolve-api provider provider-config)
    "claude-sdk"         (claude-sdk/chat request)
    "grover"             (grover/chat request)
    "anthropic-messages" (anthropic/chat request {:provider-config provider-config})
    "openai-compatible"  (openai-compat/chat request {:provider-config provider-config})
    (ollama/chat request (ollama-opts provider-config))))

(defn- provider-chat-stream [provider provider-config request on-chunk]
  (case (resolve-api provider provider-config)
    "claude-sdk"         (claude-sdk/chat-stream request on-chunk)
    "grover"             (grover/chat-stream request on-chunk {:provider-config provider-config})
    "anthropic-messages" (anthropic/chat-stream request on-chunk {:provider-config provider-config})
    "openai-compatible"  (openai-compat/chat-stream request on-chunk {:provider-config provider-config})
    (ollama/chat-stream request on-chunk (ollama-opts provider-config))))

(defn- provider-chat-with-tools [provider provider-config request tool-fn]
  (case (resolve-api provider provider-config)
    "claude-sdk"         (provider-chat provider provider-config request)
    "grover"             (grover/chat-with-tools request tool-fn)
    "anthropic-messages" (anthropic/chat-with-tools request tool-fn {:provider-config provider-config})
    "openai-compatible"  (openai-compat/chat-with-tools request tool-fn {:provider-config provider-config})
    (ollama/chat-with-tools request tool-fn (ollama-opts provider-config))))

(defn- response-preview [result]
  (let [content   (or (get-in result [:message :content])
                      (get-in result [:response :message :content]))
        tool-calls (or (get-in result [:message :tool_calls])
                       (get-in result [:response :message :tool_calls]))
        preview    (when (string? content)
                     (subs content 0 (min 200 (count content))))]
    (cond-> {}
      preview    (assoc :content-preview preview)
      tool-calls (assoc :tool-calls-count (count tool-calls)))))

(defn- log-dispatch-result [provider result error-event response-event]
  (if (:error result)
    (log/error error-event :provider provider :error (:error result) :status (:status result))
    (log/debug response-event (merge {:provider provider :model (:model result)}
                      (response-preview result))))
  result)

(defn dispatch-chat [provider provider-config request]
  (log/debug :chat/request :provider provider :model (:model request))
  (log-dispatch-result provider
                       (provider-chat provider provider-config request)
                       :chat/error
                       :chat/response))

(defn dispatch-chat-stream [provider provider-config request on-chunk]
  (log/debug :chat/stream-request :provider provider :model (:model request))
  (log-dispatch-result provider
                       (provider-chat-stream provider provider-config request on-chunk)
                       :chat/stream-error
                       :chat/stream-response))

(defn dispatch-chat-with-tools [provider provider-config request tool-fn]
  (log/debug :chat/request-with-tools :provider provider :model (:model request))
  (log-dispatch-result provider
                       (provider-chat-with-tools provider provider-config request tool-fn)
                       :chat/error
                       :chat/response))

(defn run-tool-calls! [sdir key-str tool-results]
  (doseq [[tc result] tool-results]
    (storage/append-message! sdir key-str
                             {:role    "assistant"
                              :content [{:type      "toolCall"
                                         :id        (:id tc)
                                         :name      (:name tc)
                                         :arguments (:arguments tc)}]})
    (let [error? (str/starts-with? (str result) "Error:")]
      (storage/append-message! sdir key-str
                               (cond-> {:role "toolResult" :id (:id tc) :content result}
                                 error? (assoc :isError true))))))

;; endregion ^^^^^ Provider Dispatch ^^^^^

;; region ----- REPL Loop -----

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
        result       (dispatch-chat-stream provider provider-config request
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

(defn log-compaction-check! [key-str provider model total-tokens context-window]
  (log/debug :context/compaction-check
             :session key-str
             :provider provider
             :model model
             :totalTokens total-tokens
             :contextWindow context-window))

(defn log-compaction-started! [key-str provider model total-tokens context-window]
  (log/info :context/compaction-started
            :session key-str
            :provider provider
            :model model
            :totalTokens total-tokens
            :contextWindow context-window))

(def ^:private max-compaction-attempts 5)

(defn- session-entry [sdir key-str]
  (let [agent-id (:agent (storage/parse-key key-str))]
    (->> (storage/list-sessions sdir agent-id)
         (filter #(= key-str (:key %)))
         first)))

(defn check-compaction! [sdir key-str {:keys [model soul context-window provider provider-config]}]
  (loop [attempt 1]
    (let [entry        (session-entry sdir key-str)
          total-tokens (:totalTokens entry 0)]
      (log-compaction-check! key-str provider model total-tokens context-window)
      (when (ctx/should-compact? entry context-window)
        (cond
          (> attempt max-compaction-attempts)
          (log/warn :context/compaction-stopped
                    :session key-str
                    :provider provider
                    :model model
                    :reason :max-attempts
                    :attempt attempt
                    :totalTokens total-tokens
                    :contextWindow context-window)

          :else
          (do
            (log-compaction-started! key-str provider model total-tokens context-window)
            (println "  [compacting context...]")
            (let [result (ctx/compact! sdir key-str
                                       {:model          model
                                        :soul           soul
                                        :context-window context-window
                                        :chat-fn        (partial dispatch-chat provider provider-config)})]
              (if (:error result)
                (log/error :context/compaction-failed :session key-str)
                (let [updated-total (:totalTokens (session-entry sdir key-str) 0)]
                  (if (>= updated-total total-tokens)
                    (log/warn :context/compaction-stopped
                              :session key-str
                              :provider provider
                              :model model
                              :reason :no-progress
                              :attempt attempt
                              :totalTokens updated-total
                              :contextWindow context-window)
                    (recur (inc attempt))))))))))))

(defn- tool-capable-provider? [provider provider-config]
  (not (contains? #{"claude-sdk"} (resolve-api provider provider-config))))

(defn- active-tools [provider provider-config]
  (when (tool-capable-provider? provider provider-config)
    (not-empty (tool-registry/tool-definitions))))

(defn build-chat-request [provider provider-config {:keys [model soul transcript tools]}]
  (let [build-fn (if (= "anthropic-messages" (resolve-api provider provider-config)) anthropic-prompt/build prompt/build)
        p        (build-fn {:model model :soul soul :transcript transcript :tools tools})]
    (cond-> {:model (:model p) :messages (:messages p)}
      (:system p)     (assoc :system (:system p))
      (:max_tokens p) (assoc :max_tokens (:max_tokens p))
      (:tools p)      (assoc :tools (:tools p)))))

(defn extract-tokens [result]
  (let [resp  (:response result)
        usage (or (:token-counts result) (:usage resp) {})]
    {:inputTokens  (or (:inputTokens usage) (:prompt_eval_count resp) 0)
     :outputTokens (or (:outputTokens usage) (:eval_count resp) 0)
     :cacheRead    (:cacheRead usage)
     :cacheWrite   (:cacheWrite usage)}))

(defn- body-error-message [result]
  (let [body       (:body result)
        body-error (:error body)]
    (cond
      (map? body-error) (str (or (:type body-error) (name (:error result)))
                             ": "
                             (or (:message body-error) body-error))
      (string? body-error) body-error
      (map? body)         (pr-str body))))

(defn- error-message [result]
  (or (:message result)
      (body-error-message result)
      (when (:status result)
        (str "HTTP " (:status result) " " (name (:error result))
             (when-let [body (:body result)]
               (str " - " (pr-str body)))))
      (name (:error result))))

(defn- response-model [result model]
  (or (get-in result [:response :model]) model))

(defn log-response-failed! [key-str provider result]
  (log/error :chat/response-failed
             :session key-str
             :provider provider
             :error (:error result)
             :message (error-message result)))

(defn log-message-stored! [key-str model tokens]
  (log/debug :chat/message-stored
             :session key-str
             :model model
             :tokens (select-keys tokens [:inputTokens :outputTokens])))

(defn log-stream-completed! [key-str]
  (log/debug :chat/stream-completed :session key-str))

(defn- normalized-error [err]
  (if (string? err) (keyword err) err))

(defn- persisted-error [err]
  (let [normalized (normalized-error err)]
    (if (keyword? normalized) (str normalized) normalized)))

(defn- store-error! [sdir key-str result {:keys [model provider]}]
  (try
    (storage/append-message! sdir key-str
                             {:role     "error"
                              :content  (error-message result)
                              :error    (persisted-error (:error result))
                              :model    model
                              :provider provider})
    (catch Exception e
      (log/warn :chat/error-not-stored
                :session key-str
                :provider provider
                :error (.getMessage e)))))

(defn- report-error! [sdir key-str provider result opts]
  (log-response-failed! key-str provider result)
  (store-error! sdir key-str result opts)
  result)

(defn- store-response! [sdir key-str result {:keys [model provider]}]
  (let [tokens         (extract-tokens result)
        resolved-model (response-model result model)]
    (log-message-stored! key-str resolved-model tokens)
    (storage/append-message! sdir key-str
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
    (let [result (dispatch-chat-with-tools provider provider-config request recording-tool-fn)]
      (if (:error result)
        result
        (let [response (:response result)]
          {:content  (emit-response-content! channel-impl session-key response)
           :response response})))
    (if (identical? channel-impl cli-channel/channel)
      (print-streaming-response provider provider-config request)
      (let [result (dispatch-chat provider provider-config request)]
        (if (:error result)
          result
          {:content  (emit-response-content! channel-impl session-key result)
           :response result})))))

(defn- stream-and-handle-tools!
  "Streaming loop with optional tool call detection and execution.
   If recording-tool-fn is nil, tool calls in the response are not handled."
  ([provider provider-config request recording-tool-fn]
   (stream-and-handle-tools! cli-channel/channel nil provider provider-config request recording-tool-fn))
  ([channel-impl session-key provider provider-config request recording-tool-fn]
   (loop [req   request
          loops 0]
     (let [stream-result (stream-result channel-impl session-key provider provider-config req recording-tool-fn)]
       (if (:error stream-result)
         stream-result
         (let [raw-tools (get-in (:response stream-result) [:message :tool_calls])]
           (if (and (seq raw-tools) recording-tool-fn (< loops 10))
             (let [assistant-msg {:role       "assistant"
                                  :content    (or (:content stream-result) "")
                                  :tool_calls raw-tools}
                   result-msgs   (mapv (fn [tc]
                                         {:role    "tool"
                                          :content (str (recording-tool-fn
                                                          (get-in tc [:function :name])
                                                          (get-in tc [:function :arguments])))})
                                       raw-tools)
                   new-messages  (into (:messages req) (cons assistant-msg result-msgs))]
               (recur (assoc req :messages new-messages) (inc loops)))
             stream-result)))))))

(defn process-user-input!
  [sdir key-str input {:keys [model soul provider provider-config context-window channel]
                       :or   {channel cli-channel/channel}}]
  (channel/on-turn-start channel key-str input)
  (check-compaction! sdir key-str {:model model :soul soul :context-window context-window
                                   :provider provider :provider-config provider-config})
  (storage/append-message! sdir key-str {:role "user" :content input})
  (let [transcript        (storage/get-transcript sdir key-str)
        tools             (active-tools provider provider-config)
        request           (build-chat-request provider provider-config {:model model :soul soul :transcript transcript :tools tools})
        executed-tools    (atom [])
        recording-tool-fn (when tools
                            (fn [name arguments]
                              (let [tc     {:id        (str (java.util.UUID/randomUUID))
                                            :name      name
                                            :arguments arguments
                                            :type      "toolCall"}
                                    _      (channel/on-tool-call channel key-str tc)
                                    result ((tool-registry/tool-fn) name arguments)]
                                (swap! executed-tools conj [tc result])
                                (channel/on-tool-result channel key-str tc result)
                                result)))
        result            (stream-and-handle-tools! channel key-str provider provider-config request recording-tool-fn)]
    (when-not (:error result)
      (log-stream-completed! key-str))
    (when (seq @executed-tools)
      (run-tool-calls! sdir key-str @executed-tools))
    (let [response-result (process-response! sdir key-str result {:model model :provider provider})
          final-result    (or response-result result)]
      (when (:error final-result)
        (channel/on-error channel key-str final-result))
      (channel/on-turn-end channel key-str final-result)
      final-result)))

(defn- prompt-for-input []
  (print "> ")
  (flush)
  (try
    (read-line)
    (catch Exception _ nil)))

(defn- maybe-process-input! [sdir key-str input opts]
  (when-not (str/blank? input)
    (let [result (process-user-input! sdir key-str input opts)]
      (when (:error result)
        (println (str "Error: " (error-message result))))
      result)))

(defn- chat-loop [sdir key-str {:keys [soul model provider provider-config context-window]}]
  (println)
  (letfn [(step []
            (when-let [input (prompt-for-input)]
              (maybe-process-input! sdir key-str input {:model model :soul soul :context-window context-window
                                                        :provider provider :provider-config provider-config})
              (step)))]
    (step)))

;; endregion ^^^^^ REPL Loop ^^^^^

;; region ----- Entry Point -----

(defn prepare
  "Resolve config, agent, model, and session. Returns a context map."
  [{:keys [agent model resume session] :or {agent "main"}}
   & [{:keys [cfg sdir models agents] :as overrides}]]
  (let [cfg        (or cfg (config/load-config))
        agent-id   agent
        agent-cfg  (if agents
                     (get agents agent-id)
                     (config/resolve-agent cfg agent-id))
        model-info (if (and models (or model (:model agent-cfg)))
                     (let [alias     (or model (:model agent-cfg))
                           model-cfg (get models alias)]
                       {:model          (:model model-cfg)
                        :provider       (:provider model-cfg)
                        :base-url       "http://localhost:11434"
                        :context-window (:contextWindow model-cfg)})
                     (resolve-model-info cfg agent-cfg model))
        soul       (or (:soul agent-cfg)
                       (config/read-workspace-file agent-id "SOUL.md")
                       "You are Isaac, a helpful AI assistant.")
        sdir       (or sdir (state-dir cfg))
        key-str    (create-or-resume-session sdir agent-id
                                              {:resume      resume
                                               :session-key session})]
    {:agent          agent-id
     :model          (:model model-info)
     :provider       (:provider model-info)
     :base-url       (:base-url model-info)
     :context-window (:context-window model-info)
     :soul           soul
     :session-key    key-str
     :state-dir      sdir}))

(defn run [opts]
  (let [ctx (prepare opts)]
    (builtin/register-all! tool-registry/register!)
    (println (str "Isaac — agent:" (:agent ctx) " model:" (:model ctx)))
    (println (str "Session: " (:session-key ctx)))
    (let [cfg             (config/load-config)
          provider-config (or (config/resolve-provider cfg (:provider ctx))
                              {:baseUrl (:base-url ctx)})]
      (chat-loop (:state-dir ctx) (:session-key ctx)
                 {:soul            (:soul ctx)
                  :model           (:model ctx)
                  :provider        (:provider ctx)
                  :provider-config provider-config
                  :context-window  (:context-window ctx)}))))

(registry/register!
  {:name    "chat"
   :usage   "chat [options]"
   :desc    "Start an interactive chat session"
   :options [["--agent <name>"   "Use a named agent (default: main)"]
             ["--model <alias>"  "Override the agent's default model"]
             ["--resume"         "Resume the most recent session"]
             ["--session <key>"  "Resume a specific session by key"]]
   :run-fn  run})

;; endregion ^^^^^ Entry Point ^^^^^
