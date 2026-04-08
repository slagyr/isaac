(ns isaac.cli.chat
  (:require
    [clojure.string :as str]
    [isaac.cli.registry :as registry]
    [isaac.config.resolution :as config]
    [isaac.context.manager :as ctx]
    [isaac.llm.anthropic :as anthropic]
    [isaac.llm.claude-sdk :as claude-sdk]
    [isaac.llm.ollama :as ollama]
    [isaac.llm.openai-compat :as openai-compat]
    [isaac.prompt.anthropic :as anthropic-prompt]
    [isaac.prompt.builder :as prompt]
    [isaac.session.key :as key]
    [isaac.session.storage :as storage]))

;; region ----- Helpers -----

(defn- username []
  ;; TODO - MDM: use c3kit.apron.env
  (or (System/getenv "USER")
      (System/getenv "LOGNAME")
      "user"))

(defn- resolve-model-info [cfg agent-cfg model-override]
  (let [model-ref (or model-override
                      (:model agent-cfg)
                      (get-in cfg [:agents :defaults :model]))
        ;; Check if it's an alias in the agents models map
        agents-models (get-in cfg [:agents :models])
        alias-match   (get agents-models (keyword model-ref))
        ;; Parse as provider/model format
        parsed      (when-not alias-match (config/parse-model-ref model-ref))
        ;; Resolve provider
        provider-name (or (:provider alias-match) (:provider parsed))
        provider      (when provider-name (config/resolve-provider cfg provider-name))]
    (cond
      alias-match
      {:model          (:model alias-match)
       :provider       (:provider alias-match)
       :base-url       (or (:baseUrl provider) "http://localhost:11434")
       :context-window (or (:contextWindow alias-match) (:contextWindow provider) 32768)}

      parsed
      {:model         (:model parsed)
       :provider      (:provider parsed)
       :base-url      (or (:baseUrl provider) "http://localhost:11434")
       :context-window (or (:contextWindow provider) 32768)}

      :else
      {:model         model-ref
       :provider      "ollama"
       :base-url      "http://localhost:11434"
       :context-window 32768})))

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
        (str/starts-with? provider "anthropic") "anthropic-messages"
        (= provider "ollama")                   "ollama"
        :else                                   "ollama")))

(defn dispatch-chat [provider provider-config request]
  (case (resolve-api provider provider-config)
    "claude-sdk"         (claude-sdk/chat request)
    "anthropic-messages" (anthropic/chat request {:provider-config provider-config})
    "openai-compatible"  (openai-compat/chat request {:provider-config provider-config})
    (ollama/chat request {:base-url (or (:baseUrl provider-config) "http://localhost:11434")})))

(defn dispatch-chat-stream [provider provider-config request on-chunk]
  (case (resolve-api provider provider-config)
    "claude-sdk"         (claude-sdk/chat-stream request on-chunk)
    "anthropic-messages" (anthropic/chat-stream request on-chunk {:provider-config provider-config})
    "openai-compatible"  (openai-compat/chat-stream request on-chunk {:provider-config provider-config})
    (ollama/chat-stream request on-chunk {:base-url (or (:baseUrl provider-config) "http://localhost:11434")})))

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
                           (print content)
                           (flush)
                           (swap! full-content str content))
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

(defn check-compaction! [sdir key-str {:keys [model soul context-window provider provider-config]}]
  (let [agent-id (:agent (storage/parse-key key-str))
        listing  (storage/list-sessions sdir agent-id)
        entry    (first (filter #(= key-str (:key %)) listing))]
    (when (ctx/should-compact? entry context-window)
      (println "  [compacting context...]")
      (ctx/compact! sdir key-str
                    {:model          model
                     :soul           soul
                     :context-window context-window
                     :chat-fn        (partial dispatch-chat provider provider-config)}))))

(defn build-chat-request [provider provider-config {:keys [model soul transcript]}]
  (let [build-fn (if (= "anthropic-messages" (resolve-api provider provider-config)) anthropic-prompt/build prompt/build)
        p        (build-fn {:model model :soul soul :transcript transcript})]
    (cond-> {:model (:model p) :messages (:messages p)}
      (:system p)     (assoc :system (:system p))
      (:max_tokens p) (assoc :max_tokens (:max_tokens p)))))

(defn extract-tokens [result]
  (let [resp  (:response result)
        usage (or (:usage resp) {})]
    {:inputTokens  (or (:inputTokens usage) (:prompt_eval_count resp) 0)
     :outputTokens (or (:outputTokens usage) (:eval_count resp) 0)
     :cacheRead    (:cacheRead usage)
     :cacheWrite   (:cacheWrite usage)}))

(defn process-response! [sdir key-str result {:keys [model provider]}]
  (if (:error result)
    (let [body        (:body result)
          body-error  (get body :error)
          body-msg    (cond
                        (map? body-error) (str (or (:type body-error) (name (:error result)))
                                               ": "
                                               (or (:message body-error) body-error))
                        (string? body-error) body-error
                        (map? body)         (pr-str body))
          msg         (or (:message result)
                          body-msg
                          (when (:status result)
                            (str "HTTP " (:status result) " " (name (:error result))
                                 (when body (str " - " (pr-str body)))))
                  (name (:error result)))]
      (println (str "\nError: " msg)))
    (let [tokens (extract-tokens result)]
      (storage/append-message! sdir key-str
                               {:role     "assistant"
                                :content  (:content result)
                                :model    (or (get-in result [:response :model]) model)
                                :provider provider})
      (storage/update-tokens! sdir key-str tokens)
      (handle-tool-calls (:response result)))))

(defn- chat-loop [sdir key-str {:keys [soul model provider provider-config context-window]}]
  (println)
  (loop []
    (print "> ")
    (flush)
    (when-let [input (try (read-line) (catch Exception _ nil))]
      (when-not (str/blank? input)
        (storage/append-message! sdir key-str {:role "user" :content input})
        (check-compaction! sdir key-str {:model model :soul soul :context-window context-window
                                         :provider provider :provider-config provider-config})
        (let [transcript (storage/get-transcript sdir key-str)
              request    (build-chat-request provider provider-config {:model model :soul soul :transcript transcript})
              result     (print-streaming-response provider provider-config request)]
          (process-response! sdir key-str result {:model model :provider provider}))
        (println))
      (recur))))

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
