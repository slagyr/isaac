(ns isaac.cli.server
  (:require
    [clojure.tools.cli :as tools-cli]
    [isaac.cli.registry :as registry]
    [isaac.config.loader :as config]
    [isaac.logger :as log]
    [isaac.server.app :as app]
    [isaac.tool.builtin :as builtin]
    [isaac.tool.registry :as tool-registry]))

(defn block!
  "Block the current thread until interrupted."
  []
  @(promise))

(defn run [{:keys [port host] :as opts}]
  (let [home            (or (:home opts) (System/getProperty "user.home"))
        loaded-config   (config/load-config {:home home})
         effective-config (if (contains? opts :dev)
                            (assoc loaded-config :dev (:dev opts))
                            loaded-config)
         cfg             (config/server-config effective-config)
        port            (or (when port (parse-long (str port))) (:port cfg))
        host            (or host (:host cfg))
        dev             (:dev cfg)]
    (builtin/register-all! tool-registry/register!)
    (log/info :server/starting :host host :port port)
    (let [{started-port :port started-host :host} (app/start! {:cfg  effective-config
                                                               :dev  dev
                                                               :home home
                                                               :host host
                                                               :port port})]
      (log/info :server/started :host started-host :port started-port)
      (println (str "Isaac server running on " started-host ":" started-port))
      (block!))))

(def option-spec
  [["-p" "--port N" "Port to listen on (default: 6674)"]
   ["-H" "--host H" "Host to bind to (default: 0.0.0.0)"]
   ["-d" "--dev"    "Enable development reload mode"]
   ["-h" "--help"  "Show help"]])

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
  {:name    "server"
   :usage   "server [options]"
   :desc    "Start the Isaac HTTP server"
   :option-spec option-spec
   :run-fn  run-fn})
