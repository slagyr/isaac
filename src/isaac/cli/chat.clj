;; mutation-tested: 2026-04-08
(ns isaac.cli.chat
  (:require
    [clojure.string :as str]
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
    "grover"             (grover/chat-stream request on-chunk)
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

(defn- log-dispatch-result [provider result error-event response-event]
  (if (:error result)
    (log/error {:event error-event :provider provider :error (:error result) :status (:status result)})
    (log/debug {:event response-event :provider provider :model (:model result)}))
  result)

(defn dispatch-chat [provider provider-config request]
  (log/debug {:event :chat/request :provider provider :model (:model request)})
  (log-dispatch-result provider
                       (provider-chat provider provider-config request)
                       :chat/error
                       :chat/response))

(defn dispatch-chat-stream [provider provider-config request on-chunk]
  (log/debug {:event :chat/stream-request :provider provider :model (:model request)})
  (log-dispatch-result provider
                       (provider-chat-stream provider provider-config request on-chunk)
                       :chat/stream-error
                       :chat/stream-response))

(defn dispatch-chat-with-tools [provider provider-config request tool-fn]
  (log/debug {:event :chat/request-with-tools :provider provider :model (:model request)})
  (log-dispatch-result provider
                       (provider-chat-with-tools provider provider-config request tool-fn)
                       :chat/error
                       :chat/response))

(defn run-tool-calls! [sdir key-str tool-results]
  (doseq [[tc result] tool-results]
    (storage/append-message! sdir key-str
                             {:role "assistant" :type "toolCall"
                               :id   (:id tc) :name (:name tc) :arguments (:arguments tc)})
    (let [error? (str/starts-with? (str result) "Error:")]
      (storage/append-message! sdir key-str
                               (cond-> {:role "toolResult" :id (:id tc) :content result}
                                 error? (assoc :isError true))))))

;; endregion ^^^^^ Provider Dispatch ^^^^^

;; region ----- REPL Loop -----

(defn print-streaming-response [provider provider-config request]
  (let [full-content (atom "")
        final-resp   (atom nil)
        result       (dispatch-chat-stream provider provider-config request
                       (fn [chunk]
                          (when-let [content (or (get-in chunk [:message :content])
                                                 (get-in chunk [:delta :text])
                                                 (get-in chunk [:choices 0 :delta :content]))]
                            (let [seen @full-content]
                              (if (and (:done chunk)
                                       (seq seen)
                                       (str/starts-with? content seen))
                                (let [suffix (subs content (count seen))]
                                  (when (seq suffix)
                                    (print suffix)
                                    (flush)
                                    (swap! full-content str suffix)))
                                (do
                                  (print content)
                                  (flush)
                                  (swap! full-content str content)))))
                          (when (:done chunk)
                            (reset! final-resp chunk))))]
    (println)
    (if (:error result)
      result
      {:content  (or (not-empty @full-content) (get-in result [:message :content]) "")
       :response (or @final-resp result)})))

(defn- handle-tool-calls [response]
  ;; For now, tool calling in CLI is not supported — just display the intent
  (when-let [tool-calls (get-in response [:message :tool_calls])]
    (doseq [tc tool-calls]
      (println (str "  [tool call: " (get-in tc [:function :name]) "]")))))

(defn log-compaction-check! [key-str provider model total-tokens context-window]
  (log/debug {:event         :context/compaction-check
              :session       key-str
              :provider      provider
              :model         model
              :totalTokens   total-tokens
              :contextWindow context-window}))

(defn log-compaction-started! [key-str provider model total-tokens context-window]
  (log/info {:event         :context/compaction-started
             :session       key-str
             :provider      provider
             :model         model
             :totalTokens   total-tokens
             :contextWindow context-window}))

(defn check-compaction! [sdir key-str {:keys [model soul context-window provider provider-config]}]
  (let [agent-id     (:agent (storage/parse-key key-str))
        listing      (storage/list-sessions sdir agent-id)
        entry        (first (filter #(= key-str (:key %)) listing))
        total-tokens (:totalTokens entry 0)]
    (log-compaction-check! key-str provider model total-tokens context-window)
    (when (ctx/should-compact? entry context-window)
      (log-compaction-started! key-str provider model total-tokens context-window)
      (println "  [compacting context...]")
      (let [result (ctx/compact! sdir key-str
                                 {:model          model
                                  :soul           soul
                                  :context-window context-window
                                  :chat-fn        (partial dispatch-chat provider provider-config)})]
        (when (:error result)
          (log/error {:event :context/compaction-failed :session key-str}))))))

(defn- tool-capable-provider? [provider provider-config]
  (not (contains? #{"claude-sdk" "grover"} (resolve-api provider provider-config))))

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
  (log/error {:event    :chat/response-failed
              :session  key-str
              :provider provider
              :error    (:error result)
              :message  (error-message result)}))

(defn log-message-stored! [key-str model tokens]
  (log/debug {:event   :chat/message-stored
              :session key-str
              :model   model
              :tokens  (select-keys tokens [:inputTokens :outputTokens])}))

(defn log-stream-completed! [key-str]
  (log/debug {:event   :chat/stream-completed
              :session key-str}))

(defn- report-error! [key-str provider result]
  (log-response-failed! key-str provider result)
  (println (str "\nError: " (error-message result))))

(defn- store-response! [sdir key-str result {:keys [model provider]}]
  (let [tokens         (extract-tokens result)
        resolved-model (response-model result model)]
    (log-message-stored! key-str resolved-model tokens)
    (storage/append-message! sdir key-str
                             {:role     "assistant"
                              :content  (:content result)
                              :model    resolved-model
                              :provider provider})
    (storage/update-tokens! sdir key-str tokens)
    (handle-tool-calls (:response result))))

(defn process-response! [sdir key-str result {:keys [model provider]}]
  (if (:error result)
    (report-error! key-str provider result)
    (store-response! sdir key-str result {:model model :provider provider})))

(defn- process-user-input! [sdir key-str input {:keys [model soul provider provider-config context-window]}]
  (check-compaction! sdir key-str {:model model :soul soul :context-window context-window
                                    :provider provider :provider-config provider-config})
  (storage/append-message! sdir key-str {:role "user" :content input})
  (let [transcript        (storage/get-transcript sdir key-str)
        tools             (active-tools provider provider-config)
        request           (build-chat-request provider provider-config {:model model :soul soul :transcript transcript :tools tools})
        executed-tools    (atom [])
        recording-tool-fn (fn [name arguments]
                            (let [result ((tool-registry/tool-fn) name arguments)
                                  tc     {:id (str (java.util.UUID/randomUUID))
                                          :name name
                                          :arguments arguments
                                          :type "toolCall"}]
                              (swap! executed-tools conj [tc result])
                              result))
        result            (if tools
                            (dispatch-chat-with-tools provider provider-config request recording-tool-fn)
                            (print-streaming-response provider provider-config request))]
    (when (and (not tools) (not (:error result)))
      (log-stream-completed! key-str))
    (when (seq @executed-tools)
      (run-tool-calls! sdir key-str @executed-tools))
    (process-response! sdir key-str result {:model model :provider provider})
    (println)))

(defn- prompt-for-input []
  (print "> ")
  (flush)
  (try
    (read-line)
    (catch Exception _ nil)))

(defn- maybe-process-input! [sdir key-str input opts]
  (when-not (str/blank? input)
    (process-user-input! sdir key-str input opts)))

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
