(ns isaac.bridge.prompt-cli
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [clojure.tools.cli :as tools-cli]
    [isaac.comm :as comm]
    [isaac.bridge :as bridge]
    [isaac.drive.turn :as single-turn]
    [isaac.cli :as registry]
    [isaac.config.loader :as config]
    [isaac.session.context :as session-ctx]
    [isaac.session.storage :as storage]
    [isaac.tool.builtin :as builtin]
    [isaac.tool.registry :as tool-registry]))

(defn- stderr-line! [text]
  (binding [*out* *err*]
    (println text)))

(defn- tool-icon [tool-name]
  (cond
    (= "grep" tool-name)                   "🔍"
    (= "read" tool-name)                   "📖"
    (or (= "write" tool-name)
        (= "edit" tool-name))              "✏️"
    (= "exec" tool-name)                   "⚙️"
    (= "web_fetch" tool-name)              "🌐"
    (str/starts-with? tool-name "memory_") "💾"
    :else                                   "🧰"))

(defn- tool-summary [tool-call]
  (or (get-in tool-call [:arguments :pattern])
      (get-in tool-call [:arguments :command])
      (get-in tool-call [:arguments :file_path])
      (get-in tool-call [:arguments :path])
      (some-> tool-call :arguments vals first)
      ""))

(defn- compaction-error-text [payload]
  (or (:message payload)
      (some-> (:error payload) name)
      (some-> (:error payload) str)
      "unknown error"))

(deftype CollectorChannel [text-atom]
  comm/Comm
  (on-turn-start [_ _ _] nil)
  (on-text-chunk [_ _ text] (swap! text-atom str text))
  (on-tool-call [_ _ tool-call]
    (stderr-line! (str (tool-icon (:name tool-call)) " " (:name tool-call)
                       (when-let [summary (not-empty (str (tool-summary tool-call)))]
                         (str " " summary)))))
  (on-tool-cancel [_ _ _] nil)
  (on-tool-result [_ _ tool-call _]
    (stderr-line! (str "← " (:name tool-call))))
  (on-compaction-start [_ _ payload]
    (stderr-line! (str "🥬 compacting… " (:total-tokens payload))))
  (on-compaction-success [_ _ _]
    (stderr-line! "✨ compacted"))
  (on-compaction-failure [_ _ payload]
    (stderr-line! (str "🥀 compaction failed: " (compaction-error-text payload))))
  (on-compaction-disabled [_ _ payload]
    (stderr-line! (str "🪦 compaction disabled: " (name (:reason payload)))))
  (on-turn-end [_ _ _] nil))

(defn- make-collector []
  (let [text (atom "")]
    {:comm (->CollectorChannel text)
     :text text}))

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

(defn- run-base-context [home cfg crew crew-id named-models injected-crew]
  (if injected-crew
    (session-ctx/resolve-turn-context {:crew-members crew :home home :models named-models} crew-id)
    (session-ctx/resolve-turn-context {:cfg cfg :home home} crew-id)))

(defn- resolve-provider-instance [base-ctx model-ref named-models provider-configs cfg]
  (let [alias-match (when model-ref (or (get named-models model-ref) (get named-models (keyword model-ref))))
        parsed      (when (and model-ref (not alias-match)) (config/parse-model-ref model-ref))
        provider-id (or (:provider alias-match) (:provider parsed))
        prov-cfg    (or (when provider-configs (get provider-configs provider-id))
                        (when provider-id (config/resolve-provider cfg provider-id))
                        {})
        provider    (cond
                      (:provider base-ctx) (:provider base-ctx)
                      provider-id          ((requiring-resolve 'isaac.drive.dispatch/make-provider)
                                             provider-id prov-cfg)
                      :else                ((requiring-resolve 'isaac.drive.dispatch/make-provider)
                                             "ollama" {}))]
    {:alias-match alias-match
     :parsed      parsed
     :provider    provider}))

(defn- resolve-run-opts [opts]
  (let [home          (home-dir opts)
        cfg           (config/normalize-config (config/load-config {:home home}))
        crew-id       (or (when (string? (:crew opts)) (:crew opts)) "main")
        injected-crew (or (when (map? (:crew opts)) (:crew opts)) (:agents opts))
        crew          (or injected-crew (configured-crew cfg))
        named-models  (or (:models opts) (:models cfg) {})
        base-ctx      (run-base-context home cfg crew crew-id named-models injected-crew)
        model-ref     (:model opts)
        {:keys [alias-match parsed provider]} (resolve-provider-instance base-ctx model-ref named-models (:provider-configs opts) cfg)
        model-name    (or (:model alias-match) (:model parsed) model-ref (:model base-ctx))
        sdir          (or (:state-dir opts) (:stateDir cfg)
                          (str (System/getProperty "user.home") "/.isaac"))]
    {:crew-id        crew-id
     :crew-members   crew
     :models         named-models
     :state-dir      sdir
     :soul           (:soul base-ctx)
     :model          model-name
     :provider       provider
     :context-window (or (:context-window alias-match) (:context-window base-ctx) 32768)}))

(defn run [opts]
  (if-not (:message opts)
    (do (println "Error: -m/--message is required")
        1)
    (if (= false (ensure-local-config! opts))
      1
        (let [{:keys [crew-id crew-members models state-dir soul model provider context-window]}
             (resolve-run-opts opts)
             resumed-key (when (:resume opts)
                          (:id (storage/most-recent-session state-dir crew-id)))
             session-key (or (:session opts) resumed-key "prompt-default")
             {:keys [comm text]} (make-collector)]
        (or (storage/open-session state-dir session-key)
            (storage/create-session! state-dir session-key {:crew   crew-id
                                                            :origin {:kind :cli}}))
        (builtin/register-all! tool-registry/register!)
          (let [result (bridge/dispatch!
                        state-dir
                        {:session-key    session-key
                         :input          (:message opts)
                         :model          model
                         :crew-members   crew-members
                         :models         models
                         :soul           soul
                         :provider       provider
                         :context-window context-window
                         :comm           comm})]
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
