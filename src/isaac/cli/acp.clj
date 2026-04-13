(ns isaac.cli.acp
  (:require
    [cheshire.core :as json]
    [clojure.tools.cli :as tools-cli]
    [isaac.acp.rpc :as rpc]
    [isaac.acp.server :as server]
    [isaac.acp.ws :as ws]
    [isaac.cli.registry :as registry]
    [isaac.config.resolution :as config]
    [isaac.logger :as log]
    [isaac.session.storage :as storage]
    [isaac.tool.builtin :as builtin]
    [isaac.tool.registry :as tool-registry]))

(def option-spec
  [["-v" "--verbose"      "Log inbound method names to stderr"]
   ["-s" "--session KEY"  "Attach to an existing session key"]
   ["-m" "--model ALIAS"  "Override the agent's default model"]
   ["-a" "--agent NAME"   "Use a named agent (default: main)"]
   ["-R" "--resume"       "Resume the most recent session for the agent"]
   ["-r" "--remote URL"   "Proxy ACP over a remote WebSocket endpoint"]
   ["-t" "--token TOKEN"  "Bearer token for remote ACP authentication"]
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

(defn- find-most-recent-session [state-dir agent-id]
  (->> (storage/list-sessions state-dir agent-id)
       (filter #(= "acp" (:channel %)))
       (sort-by :updatedAt)
       last))

(defn- resumed-session-key [state-dir agent-id]
  (some-> (find-most-recent-session state-dir agent-id) :key))

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

(defn- write-line! [line]
  (.write *out* line)
  (.write *out* "\n")
  (.flush *out*))

(defn- request-id [line]
  (try
    (:id (json/parse-string line true))
    (catch Exception _ nil)))

(defn- message-method [line]
  (try
    (:method (json/parse-string line true))
    (catch Exception _ nil)))

(defn- authentication-error? [error]
  (let [cause      (or (ex-cause error) error)
        class-name (.getName (class cause))
        message    (or (.getMessage cause) "")]
    (or (= "java.net.http.WebSocketHandshakeException" class-name)
        (re-find #"(?i)401|unauthorized|authentication failed" message))))

(defn- remote-headers [token]
  (cond-> {}
    token (assoc "Authorization" (str "Bearer " token))))

(defn- proxy-remote-request! [conn url line]
  (log/debug :ws/message-sent
             :method (message-method line)
             :url    url)
  (ws/ws-send! conn line)
  (when-let [id (request-id line)]
    (loop []
      (when-let [message-line (ws/ws-receive! conn)]
        (if-let [error (:error message-line)]
          (throw error)
          (do
            (log/debug :ws/message-received
                       :method (message-method message-line)
                       :url    url)
            (write-line! message-line)
            (when-not (= id (request-id message-line))
              (recur))))))))

(defn- run-remote [opts]
  (let [url     (:remote opts)
        token   (:token opts)
        factory (or (:ws-connection-factory opts) ws/connect!)]
    (try
      (let [conn   (factory url {:headers (remote-headers token)})
            reader (java.io.BufferedReader. *in*)]
        (log/debug :ws/connection-opened :url url)
        (try
          (loop []
            (when-let [line (.readLine reader)]
              (proxy-remote-request! conn url line)
              (recur)))
          (finally
            (log/debug :ws/connection-closed :url url)
            (ws/ws-close! conn)))
        0)
      (catch Exception e
        (print-error! (if (authentication-error? e)
                        "authentication failed"
                        (str "could not connect to remote ACP endpoint: " url)))
        1))))

(defn run [opts]
  (let [server-opts  (build-server-opts opts)
        agent-id     (or (:agent opts) "main")
        remote-url   (:remote opts)
        model-alias  (:model opts)
        session-key  (:session opts)
        resume?      (:resume opts)
        resumed-key  (when resume?
                       (resumed-session-key (:state-dir server-opts) agent-id))
        attach-key   (or session-key resumed-key)]
    (cond
      (and resume? model-alias)
      (do (print-error! "cannot combine --resume with --model") 1)

      remote-url
      (run-remote opts)

      (and model-alias (not (valid-model? server-opts model-alias)))
      (do (print-error! (str "unknown model: " model-alias)) 1)

      (and session-key (not (session-exists? (:state-dir server-opts) session-key)))
      (do (print-error! (str "session not found: " session-key)) 1)

      :else
      (let [server-opts' (cond-> server-opts
                            model-alias (assoc :model-override model-alias))
             handlers     (cond-> (server/handlers server-opts')
                            attach-key (attach-session-handler attach-key))]
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
