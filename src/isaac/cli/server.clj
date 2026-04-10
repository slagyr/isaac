(ns isaac.cli.server
  (:require
    [isaac.cli.registry :as registry]
    [isaac.config.resolution :as config]
    [isaac.logger :as log]
    [isaac.server.app :as app]))

(defn block!
  "Block the current thread until interrupted."
  []
  @(promise))

(defn run [{:keys [port host] :as opts}]
  (let [loaded-config   (config/load-config)
        effective-config (if (contains? opts :dev)
                           (assoc loaded-config :dev (:dev opts))
                           loaded-config)
        cfg             (config/server-config effective-config)
        port            (or (when port (parse-long (str port))) (:port cfg))
        host            (or host (:host cfg))
        dev             (:dev cfg)]
    (log/info :server/starting :host host :port port)
    (let [{started-port :port started-host :host} (app/start! {:port port :host host :dev dev})]
      (log/info :server/started :host started-host :port started-port)
      (println (str "Isaac server running on " started-host ":" started-port))
      (block!))))

(registry/register!
  {:name    "server"
   :usage   "server [options]"
   :desc    "Start the Isaac HTTP server"
   :options [["--port <n>"   "Port to listen on (default: 6674)"]
             ["--host <h>"   "Host to bind to (default: 0.0.0.0)"]
             ["--dev"        "Enable development reload mode"]]
   :run-fn  run})
