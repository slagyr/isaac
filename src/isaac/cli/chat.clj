(ns isaac.cli.chat
  (:require
    [clojure.string :as str]
    [isaac.cli.registry :as registry]
    [isaac.config.resolution :as config]
    [isaac.context.manager :as ctx]
    [isaac.llm.ollama :as ollama]
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
  (let [models   (get-in cfg [:agents :models])
        alias    (or model-override (:model agent-cfg)
                     (get-in cfg [:agents :defaults :model]))
        model-ref (or model-override
                      (:model agent-cfg)
                      (get-in cfg [:agents :defaults :model]))
        parsed   (config/parse-model-ref model-ref)
        provider (when parsed (config/resolve-provider cfg (:provider parsed)))]
    (if parsed
      {:model         (:model parsed)
       :provider      (:provider parsed)
       :base-url      (or (:baseUrl provider) "http://localhost:11434")
       :context-window (or (:contextWindow provider) 32768)}
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
    (do (println (str "Resuming session: " session-key))
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

;; region ----- REPL Loop -----

(defn- print-streaming-response [request base-url]
  (let [full-content (atom "")
        final-resp   (atom nil)]
    (let [result (ollama/chat-stream
                   request
                   (fn [chunk]
                     (when-let [content (get-in chunk [:message :content])]
                       (print content)
                       (flush)
                       (swap! full-content str content))
                     (when (:done chunk)
                       (reset! final-resp chunk)))
                   {:base-url base-url})]
      (println)
      (if (:error result)
        result
        {:content  @full-content
         :response (or @final-resp result)}))))

(defn- handle-tool-calls [sdir key-str response base-url soul model-str tools]
  ;; For now, tool calling in CLI is not supported — just display the intent
  (when-let [tool-calls (get-in response [:message :tool_calls])]
    (doseq [tc tool-calls]
      (println (str "  [tool call: " (get-in tc [:function :name]) "]")))))

(defn- chat-loop [sdir key-str {:keys [soul model base-url context-window]}]
  (println)
  (loop []
    (print "> ")
    (flush)
    (when-let [input (try (read-line) (catch Exception _ nil))]
      (when-not (str/blank? input)
        ;; Append user message
        (storage/append-message! sdir key-str {:role "user" :content input})

        ;; Check for compaction
        (let [agent-id (:agent (storage/parse-key key-str))
              listing  (storage/list-sessions sdir agent-id)
              entry    (first (filter #(= key-str (:key %)) listing))]
          (when (ctx/should-compact? entry context-window)
            (println "  [compacting context...]")
            (ctx/compact! sdir key-str
                          {:model          model
                           :soul           soul
                           :context-window context-window
                           :chat-fn        ollama/chat})))

        ;; Build and send prompt
        (let [transcript (storage/get-transcript sdir key-str)
              p          (prompt/build {:model      model
                                        :soul       soul
                                        :transcript transcript})
              request    {:model    (:model p)
                          :messages (:messages p)}
              result     (print-streaming-response request base-url)]
          (if (:error result)
            (println (str "\nError: " (:message result)))
            (let [resp (:response result)]
              ;; Append assistant message
              (storage/append-message! sdir key-str
                                       {:role     "assistant"
                                        :content  (:content result)
                                        :model    model
                                        :provider "ollama"})
              ;; Update token counts
              (storage/update-tokens! sdir key-str
                                      {:inputTokens  (or (:prompt_eval_count resp) 0)
                                       :outputTokens (or (:eval_count resp) 0)})
              ;; Handle tool calls if present
              (handle-tool-calls sdir key-str resp base-url soul model nil))))
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
    (chat-loop (:state-dir ctx) (:session-key ctx)
               {:soul           (:soul ctx)
                :model          (:model ctx)
                :base-url       (:base-url ctx)
                :context-window (:context-window ctx)})))

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
