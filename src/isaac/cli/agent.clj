(ns isaac.cli.agent
  (:require
    [cheshire.core :as json]
    [isaac.channel :as channel]
    [isaac.cli.chat :as chat]
    [isaac.cli.registry :as registry]
    [isaac.config.resolution :as config]
    [isaac.session.storage :as storage]))

(deftype CollectorChannel [text-atom]
  channel/Channel
  (on-turn-start [_ _ _] nil)
  (on-text-chunk [_ _ text] (swap! text-atom str text))
  (on-tool-call [_ _ _] nil)
  (on-tool-result [_ _ _ _] nil)
  (on-turn-end [_ _ _] nil)
  (on-error [_ _ _] nil))

(defn- make-collector []
  (let [text (atom "")]
    {:channel (->CollectorChannel text)
     :text    text}))

(defn- resolve-run-opts [opts]
  (let [agent-id (or (:agent opts) "main")
        agents   (or (:agents opts)
                     (let [cfg (config/load-config)]
                       {"main" (config/resolve-agent cfg agent-id)}))
        models   (or (:models opts)
                     (let [cfg (config/load-config)]
                       (get-in cfg [:agents :models] {})))
        sdir     (or (:state-dir opts)
                     (let [cfg (config/load-config)]
                       (or (:stateDir cfg) (str (System/getProperty "user.home") "/.isaac"))))
        agent-cfg  (get agents agent-id)
        model-alias (:model agent-cfg)
        model-cfg  (get models model-alias)
        provider   (:provider model-cfg)
        prov-cfg   (or (when (:provider-configs opts)
                         (get (:provider-configs opts) provider))
                       (let [cfg (config/load-config)]
                         (config/resolve-provider cfg provider))
                       {})]
    {:agent-id        agent-id
     :state-dir       sdir
     :soul            (or (:soul agent-cfg) "You are Isaac, a helpful AI assistant.")
     :model           (:model model-cfg)
     :provider        provider
     :provider-config prov-cfg
     :context-window  (:contextWindow model-cfg 32768)}))

(defn- default-session-key [agent-id]
  (str "agent:" agent-id ":main"))

(defn run [opts]
  (if-not (:message opts)
    (do (println "Error: -m/--message is required")
        1)
    (let [{:keys [agent-id state-dir soul model provider provider-config context-window]}
          (resolve-run-opts opts)
          session-key (or (:session opts) (default-session-key agent-id))
          {:keys [channel text]} (make-collector)]
      (storage/create-session! state-dir session-key)
      (let [result (chat/process-user-input!
                     state-dir session-key (:message opts)
                     {:model           model
                      :soul            soul
                      :provider        provider
                      :provider-config provider-config
                      :context-window  context-window
                      :channel         channel})]
        (if (or (:error result) (get-in result [:response :error]))
          1
          (do
            (if (:json opts)
              (println (json/generate-string {:session  session-key
                                              :response @text}))
              (println @text))
            0))))))

(registry/register!
  {:name    "agent"
   :usage   "agent -m <message> [options]"
   :desc    "Run a single agent turn and exit"
   :options [["-m, --message <text>" "Message to send (required)"]
             ["--session <key>"      "Session key (default: agent:main:main)"]
             ["--agent <id>"         "Agent id (default: main)"]
             ["--model <alias>"      "Override agent's default model"]
             ["--json"               "Output result as JSON"]]
   :run-fn  run})
