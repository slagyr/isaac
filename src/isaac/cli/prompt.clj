(ns isaac.cli.prompt
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [clojure.tools.cli :as tools-cli]
    [isaac.comm :as comm]
    [isaac.drive.turn :as single-turn]
    [isaac.cli.registry :as registry]
    [isaac.config.loader :as config]
    [isaac.session.context :as session-ctx]
    [isaac.session.storage :as storage]
    [isaac.tool.builtin :as builtin]
    [isaac.tool.registry :as tool-registry]))

(deftype CollectorChannel [text-atom]
  comm/Comm
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

(defn- configured-crew [cfg]
  (:crew (config/normalize-config cfg)))

(defn- home-dir [{:keys [home state-dir]}]
  (or home state-dir (System/getProperty "user.home")))

(defn- print-error! [message]
  (binding [*out* *err*]
    (println message)))

(defn- ensure-local-config! [opts]
  (when-not (or (map? (:crew opts))
                (map? (:agents opts)))
    (let [result (config/load-config-result {:home (home-dir opts)})]
      (when (:missing-config? result)
        (print-error! (get-in result [:errors 0 :value]))
        false))))

(defn- resolve-run-opts [opts]
  (let [home         (home-dir opts)
        cfg          (config/normalize-config (config/load-config {:home home}))
        crew-id      (or (when (string? (:crew opts)) (:crew opts)) "main")
        injected-crew (or (when (map? (:crew opts)) (:crew opts)) (:agents opts))
        crew         (or injected-crew (configured-crew cfg))
        crew-cfg     (or (get crew crew-id) (config/resolve-crew cfg crew-id))
        named-models (or (:models opts) (:models cfg) {})
        base-ctx     (if injected-crew
                        (session-ctx/resolve-turn-context {:crew-members crew :home home :models named-models} crew-id)
                        (session-ctx/resolve-turn-context {:cfg cfg :home home} crew-id))
        model-ref    (:model opts)
        alias-match  (when model-ref (or (get named-models model-ref) (get named-models (keyword model-ref))))
        parsed       (when (and model-ref (not alias-match)) (config/parse-model-ref model-ref))
        provider     (or (:provider alias-match) (:provider parsed) (:provider base-ctx) "ollama")
        model-name   (or (:model alias-match) (:model parsed) model-ref (:model base-ctx))
        prov-cfg     (or (when (:provider-configs opts) (get (:provider-configs opts) provider))
                          (:provider-config base-ctx)
                          (config/resolve-provider cfg provider)
                          {})
        sdir         (or (:state-dir opts) (:stateDir cfg)
                          (str (System/getProperty "user.home") "/.isaac"))]
    {:crew-id         crew-id
      :crew-members    crew
      :models          named-models
      :state-dir       sdir
      :soul            (:soul base-ctx)
      :model           model-name
      :provider        provider
      :provider-config prov-cfg
      :context-window  (or (:context-window alias-match) (:context-window base-ctx) 32768)}))

(defn run [opts]
  (if-not (:message opts)
    (do (println "Error: -m/--message is required")
        1)
    (if (= false (ensure-local-config! opts))
      1
        (let [{:keys [crew-id crew-members models state-dir soul model provider provider-config context-window]}
             (resolve-run-opts opts)
             resumed-key (when (:resume opts)
                          (:id (storage/most-recent-session state-dir crew-id)))
             session-key (or (:session opts) resumed-key "prompt-default")
             {:keys [channel text]} (make-collector)]
        (or (storage/open-session state-dir session-key)
            (storage/create-session! state-dir session-key {:crew   crew-id
                                                            :origin {:kind :cli}}))
        (builtin/register-all! tool-registry/register!)
          (let [result (single-turn/process-user-input!
                       state-dir session-key (:message opts)
                       {:model           model
                          :crew-members    crew-members
                          :models          models
                          :soul            soul
                          :provider        provider
                           :provider-config provider-config
                          :context-window  context-window
                          :channel         channel})]
           (if (or (:error result) (get-in result [:response :error]))
             (do
               (binding [*out* *err*]
                 (println (single-turn/error-message result)))
               1)
             (do
               (if (:json opts)
                 (println (json/generate-string {:session  session-key
                                                 :response @text}))
                 (println @text))
               0)))))))

(def option-spec
  [["-m" "--message TEXT"  "Message to send (required)"]
   ["-s" "--session KEY"    "Session id (default: prompt-default)"]
   ["-R" "--resume"         "Resume the most recent session"]
   ["-c" "--crew ID"        "Crew member id (default: main)"]
   ["-M" "--model ALIAS"    "Override crew member's default model"]
   ["-j" "--json"           "Output result as JSON"]
   ["-h" "--help"           "Show help"]])

(defn- parse-option-map [raw-args]
  (let [{:keys [arguments options errors]} (tools-cli/parse-opts raw-args option-spec)]
    {:options (->> options
                   (remove (comp nil? val))
                   (into {}))
     :arguments arguments
     :errors  errors}))

(defn run-fn [{:keys [_raw-args] :as opts}]
  (let [{:keys [arguments options errors]} (parse-option-map (or _raw-args []))
        options (cond-> options
                  (and (nil? (:message options)) (seq arguments))
                  (assoc :message (str/join " " arguments)))]
    (cond
      (:help options)
      (do
        (println (registry/command-help (registry/get-command "prompt")))
        0)

      (seq errors)
      (do
        (doseq [error errors]
          (println error))
        1)

      :else
      (run (merge (dissoc opts :_raw-args) options)))))

(registry/register!
  {:name    "prompt"
   :usage   "prompt -m <message> [options]"
   :desc    "Run a single prompt turn and exit"
   :option-spec option-spec
   :run-fn  run-fn})
