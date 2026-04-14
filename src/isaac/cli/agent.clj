(ns isaac.cli.agent
  (:require
    [cheshire.core :as json]
    [clojure.tools.cli :as tools-cli]
    [isaac.channel :as channel]
    [isaac.cli.chat.single-turn :as single-turn]
    [isaac.cli.registry :as registry]
    [isaac.config.resolution :as config]
    [isaac.session.storage :as storage]
    [isaac.tool.builtin :as builtin]
    [isaac.tool.registry :as tool-registry]))

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
  (let [cfg        (config/load-config)
        agent-id   (or (:agent opts) "main")
        agents     (or (:agents opts) {"main" (config/resolve-agent cfg agent-id)})
        agent-cfg  (get agents agent-id)
        model-ref  (or (:model opts) (:model agent-cfg) (get-in cfg [:agents :defaults :model]))
        ;; Named alias lookup (string key for test injection, keyword for config)
        named-models (or (:models opts) (get-in cfg [:agents :models]) {})
        alias-match  (or (get named-models model-ref) (get named-models (keyword model-ref)))
        ;; Fallback: parse as provider/model format
        parsed       (when-not alias-match (config/parse-model-ref model-ref))
        provider     (or (:provider alias-match) (:provider parsed) "ollama")
        model-name   (or (:model alias-match) (:model parsed) model-ref)
        prov-cfg     (or (when (:provider-configs opts) (get (:provider-configs opts) provider))
                         (config/resolve-provider cfg provider)
                         {})
        sdir         (or (:state-dir opts) (:stateDir cfg)
                         (str (System/getProperty "user.home") "/.isaac"))]
    {:agent-id        agent-id
     :state-dir       sdir
     :soul            (or (:soul agent-cfg) "You are Isaac, a helpful AI assistant.")
     :model           model-name
     :provider        provider
     :provider-config prov-cfg
     :context-window  (or (:contextWindow alias-match) 32768)}))

(defn- default-session-key [agent-id]
  "agent-default")

(defn run [opts]
  (if-not (:message opts)
    (do (println "Error: -m/--message is required")
        1)
    (let [{:keys [agent-id state-dir soul model provider provider-config context-window]}
     (resolve-run-opts opts)
           session-key (or (:session opts) (default-session-key agent-id))
           {:keys [channel text]} (make-collector)]
      (or (storage/open-session state-dir session-key)
          (storage/create-session! state-dir session-key {:agent agent-id}))
      (builtin/register-all! tool-registry/register!)
      (let [result (single-turn/process-user-input!
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

(def option-spec
  [["-m" "--message TEXT"  "Message to send (required)"]
   ["-s" "--session KEY"    "Session id (default: agent-default)"]
   ["-a" "--agent ID"       "Agent id (default: main)"]
   ["-M" "--model ALIAS"    "Override agent's default model"]
   ["-j" "--json"           "Output result as JSON"]
   ["-h" "--help"           "Show help"]])

(defn- parse-option-map [raw-args]
  (let [{:keys [options errors]} (tools-cli/parse-opts raw-args option-spec)]
    {:options (->> options
                   (remove (comp nil? val))
                   (into {}))
     :errors  errors}))

(defn run-fn [{:keys [_raw-args] :as opts}]
  (let [{:keys [options errors]} (parse-option-map (or _raw-args []))]
    (cond
      (:help options)
      (do
        (println (registry/command-help (registry/get-command "agent")))
        0)

      (seq errors)
      (do
        (doseq [error errors]
          (println error))
        1)

      :else
      (run (merge (dissoc opts :_raw-args) options)))))

(registry/register!
  {:name    "agent"
   :usage   "agent -m <message> [options]"
   :desc    "Run a single agent turn and exit"
   :option-spec option-spec
   :run-fn  run-fn})
