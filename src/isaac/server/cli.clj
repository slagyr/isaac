;; mutation-tested: 2026-05-06
(ns isaac.server.cli
  (:require
    [clojure.string :as str]
    [clojure.tools.cli :as tools-cli]
    [isaac.cli :as registry]
    [isaac.config.loader :as config]
    [isaac.log-viewer :as viewer]
    [isaac.logger :as log]
    [isaac.server.app :as app]
    [isaac.tool.builtin :as builtin]
    [isaac.tool.registry :as tool-registry]))

(defn block!
  "Block the current thread until interrupted."
  []
  @(promise))

(def ^:private server-log-prelude-limit 10)

(defn- start-log-tail! [log-path state-dir {:keys [no-color zebra]}]
  (let [color? (not no-color)
        zebra? (boolean zebra)
        path   (cond
                 (nil? log-path)                         nil
                 (str/starts-with? log-path "/")         log-path
                 (and state-dir (seq state-dir))         (str state-dir "/" log-path)
                 :else                                   log-path)]
    (when path
      (let [f (java.io.File. path)]
        (.mkdirs (or (.getParentFile f) (java.io.File. ".")))
        (when-not (.exists f) (.createNewFile f)))
      (future (viewer/tail! path {:color?  color?
                                  :zebra?  zebra?
                                  :follow? true
                                  :limit   server-log-prelude-limit}))
      path)))

(defn run [{:keys [port host logs] :as opts}]
  (let [home             (or (:home opts) (System/getProperty "user.home"))
        state-dir        (str home "/.isaac")
        loaded-config    (config/load-config {:home home})
        effective-config (if (contains? opts :dev)
                           (assoc loaded-config :dev (:dev opts))
                           loaded-config)
        cfg              (config/server-config effective-config)
        port             (or (when port (parse-long (str port))) (:port cfg))
        host             (or host (:host cfg))
        dev              (:dev cfg)]
    (when logs
      (when-let [abs-path (start-log-tail! (log/log-file) state-dir opts)]
        (log/set-log-file! abs-path)
        (log/set-output! :file)))
    (builtin/register-all!)
    (log/info :server/starting :host host :port port)
    (let [{started-port :port started-host :host} (app/start! {:cfg       effective-config
                                                               :dev       dev
                                                               :host      host
                                                               :port      port
                                                               :state-dir state-dir})]
      (log/info :server/started :host started-host :port started-port)
      (println (str "Isaac server running on " started-host ":" started-port))
      (block!))))

(def option-spec
  [["-p" "--port N" "Port to listen on (default: 6674)"]
   ["-H" "--host H" "Host to bind to (default: 0.0.0.0)"]
   ["-d" "--dev" "Enable development reload mode"]
   [nil  "--logs" "Tail and print the log file while the server runs"]
   [nil  "--no-color" "Disable color output for --logs"]
   [nil  "--zebra" "Enable zebra striping for --logs"]
   ["-h" "--help" "Show help"]])

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
        (println (registry/command-help (registry/get-command "server")))
        0)

      (seq errors)
      (do
        (doseq [error errors]
          (println error))
        1)

      :else
      (run (merge (dissoc opts :_raw-args) options)))))

(registry/register!
  {:name        "server"
   :usage       "server [options]"
   :desc        "Start the Isaac HTTP server"
   :option-spec option-spec
   :run-fn      run-fn})
