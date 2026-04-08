(ns isaac.cli.serve
  (:require
    [isaac.cli.registry :as registry]
    [isaac.logger :as log]
    [isaac.server.app :as app]))

(defn block!
  "Block the current thread until interrupted."
  []
  @(promise))

(defn run [{:keys [port host gateway-port gateway-host]}]
  (let [port (or (when port (parse-long (str port)))
                 (when gateway-port (parse-long (str gateway-port)))
                 3000)
        host (or host gateway-host "0.0.0.0")]
    (log/info {:event :server/starting :host host :port port})
    (let [{:keys [port]} (app/start! {:port port})]
      (log/info {:event :server/started :host host :port port})
      (println (str "Isaac server running on " host ":" port))
      (block!))))

(registry/register!
  {:name    "server"
   :usage   "server [options]"
   :desc    "Start the Isaac HTTP server"
   :options [["--port <n>"   "Port to listen on (default: 3000)"]
             ["--host <h>"   "Host to bind to (default: 0.0.0.0)"]]
   :run-fn  run})
