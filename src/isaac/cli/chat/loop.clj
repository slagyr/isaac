(ns isaac.cli.chat.loop
  (:require
    [clojure.string :as str]
    [isaac.cli.chat.single-turn :as single-turn]
    [isaac.config.resolution :as config]
    [isaac.session.bridge :as bridge]
    [isaac.session.key :as key]
    [isaac.session.storage :as storage]))

;; region ----- Helpers -----

(defn- username []
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
  (let [model-ref     (or model-override
                          (:model agent-cfg)
                          (get-in cfg [:agents :defaults :model]))
        agents-models (get-in cfg [:agents :models])
        alias-match   (get agents-models (keyword model-ref))
        parsed        (when-not alias-match (config/parse-model-ref model-ref))
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

;; region ----- Chat Loop -----

(defn- prompt-for-input []
  (print "> ")
  (flush)
  (try
    (read-line)
    (catch Exception _ nil)))

(defn- turn-fn [sdir key-str opts]
  (fn [input _ctx]
    (single-turn/process-user-input! sdir key-str input opts)))

(defn- handle-bridge-result! [result]
  (case (:type result)
    :command
    (case (:command result)
      :status  (println (bridge/format-status (:data result)))
      :unknown (println (:message result))
      nil)

    :turn
    (when (:error (:result result))
      (println (str "Error: " (single-turn/error-message (:result result)))))

    nil))

(defn- maybe-dispatch! [sdir key-str input ctx opts]
  (when-not (str/blank? input)
    (let [result (bridge/dispatch sdir key-str input ctx (turn-fn sdir key-str opts))]
      (handle-bridge-result! result)
      result)))

(defn chat-loop [sdir key-str {:keys [soul model provider provider-config context-window]}]
  (println)
  (let [agent-id (:agent (storage/parse-key key-str))
        ctx      {:agent agent-id :model model :provider provider :context-window context-window}
        opts     {:model model :soul soul :context-window context-window :provider provider :provider-config provider-config}]
    (letfn [(step []
              (when-let [input (prompt-for-input)]
                (maybe-dispatch! sdir key-str input ctx opts)
                (step)))]
      (step))))

;; endregion ^^^^^ Chat Loop ^^^^^

;; region ----- Public API -----

(defn prepare
  "Resolve config, agent, model, and session. Returns a context map."
  [{:keys [agent model resume session] :or {agent "main"}}
   & [{:keys [cfg sdir models agents]}]]
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

;; endregion ^^^^^ Public API ^^^^^
