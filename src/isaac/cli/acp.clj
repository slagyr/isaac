(ns isaac.cli.acp
  (:require
    [clojure.tools.cli :as tools-cli]
    [isaac.acp.rpc :as rpc]
    [isaac.acp.server :as server]
    [isaac.cli.registry :as registry]
    [isaac.config.resolution :as config]
    [isaac.session.storage :as storage]
    [isaac.tool.builtin :as builtin]
    [isaac.tool.registry :as tool-registry]))

(def option-spec
  [["-v" "--verbose"      "Log inbound method names to stderr"]
   ["-s" "--session KEY"  "Attach to an existing session key"]
   ["-m" "--model ALIAS"  "Override the agent's default model"]
   ["-a" "--agent NAME"   "Use a named agent (default: main)"]
   ["-h" "--help"         "Show help"]])

(defn- parse-option-map [raw-args]
  (let [{:keys [options errors]} (tools-cli/parse-opts raw-args option-spec)]
    {:options (->> options
                   (remove (comp nil? val))
                   (into {}))
     :errors  errors}))

(defn- valid-model? [server-opts model-alias]
  (if-let [models (:models server-opts)]
    (contains? models model-alias)
    (let [cfg          (:cfg server-opts)
          agents-models (get-in cfg [:agents :models])]
      (boolean (or (get agents-models (keyword model-alias))
                   (config/parse-model-ref model-alias))))))

(defn- build-server-opts [opts]
  (let [home      (or (:home opts) (System/getProperty "user.home"))
        cfg       (config/load-config {:home home})
        sdir      (or (:state-dir opts) (:stateDir cfg)
                      (str home "/.isaac"))
        out       (or (:output-writer opts) *out*)
        agents    (:agents opts)
        models    (:models opts)
        prov-cfgs (:provider-configs opts)
        agent-id  (:agent opts)]
    (cond-> {:state-dir sdir :home home :output-writer out}
      agents    (assoc :agents agents)
      models    (assoc :models models)
      prov-cfgs (assoc :provider-configs prov-cfgs)
      agent-id  (assoc :agent-id agent-id)
      (nil? agents) (assoc :cfg cfg))))

(defn- write-result! [result]
  (when result
    (cond
      (contains? result :notifications)
      (do (doseq [n (:notifications result)]
            (rpc/write-message! *out* n))
          (when-let [r (:response result)]
            (rpc/write-message! *out* r)))

      (contains? result :response)
      (rpc/write-message! *out* (:response result))

      :else
      (rpc/write-message! *out* result))))

(defn- session-exists? [state-dir session-key]
  (some? (storage/get-transcript state-dir session-key)))

(defn- attach-session-handler [handlers session-key]
  (assoc handlers "session/new" (fn [_ _] {:sessionId session-key})))

(defn- run-loop [handlers]
  (let [reader (java.io.BufferedReader. *in*)]
    (loop []
      (when-let [line (.readLine reader)]
        (write-result! (rpc/handle-line handlers line))
        (recur)))))

(defn- run-loop-verbose [handlers]
  (let [dispatch* rpc/dispatch]
    (with-redefs [rpc/dispatch (fn [dispatch-handlers message]
                                 (when-let [method (:method message)]
                                   (binding [*out* *err*]
                                     (println method)))
                                 (dispatch* dispatch-handlers message))]
      (run-loop handlers))))

(defn- print-error! [message]
  (binding [*out* *err*]
    (println message)))

(defn run [opts]
  (let [server-opts  (build-server-opts opts)
        model-alias  (:model opts)
        session-key  (:session opts)]
    (cond
      (and model-alias (not (valid-model? server-opts model-alias)))
      (do (print-error! (str "unknown model: " model-alias)) 1)

      (and session-key (not (session-exists? (:state-dir server-opts) session-key)))
      (do (print-error! (str "session not found: " session-key)) 1)

      :else
      (let [server-opts' (cond-> server-opts
                           model-alias (assoc :model-override model-alias))
            handlers     (cond-> (server/handlers server-opts')
                           session-key (attach-session-handler session-key))]
        (builtin/register-all! tool-registry/register!)
        (print-error! "isaac acp ready")
        (if (:verbose opts)
          (run-loop-verbose handlers)
          (run-loop handlers))
        0))))

(defn run-fn [{:keys [_raw-args] :as opts}]
  (let [{:keys [options errors]} (parse-option-map (or _raw-args []))]
    (cond
      (:help options)
      (do
        (println (registry/command-help (registry/get-command "acp")))
        0)

      (seq errors)
      (do
        (doseq [error errors]
          (println error))
        1)

      :else
      (run (merge (dissoc opts :_raw-args) options)))))

(registry/register!
  {:name        "acp"
   :usage       "acp [options]"
   :desc        "Run Isaac as an ACP agent over stdio"
   :option-spec option-spec
   :run-fn      run-fn})
