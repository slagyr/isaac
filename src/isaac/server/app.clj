(ns isaac.server.app
  (:require
    [isaac.server.http :as http]
    [org.httpkit.server :as httpkit]))

(defonce ^:private state (atom nil))

(defn running? []
  (some? @state))

(defn start! [opts]
  (when (running?) (httpkit/server-stop! (:server @state)))
  (let [port    (or (:port opts) 3000)
        handler (http/create-handler)
        server  (httpkit/run-server handler {:port port :legacy-return-value? false})
        actual  (httpkit/server-port server)]
    (reset! state {:server server :port actual})
    {:port actual}))

(defn stop! []
  (when-let [{:keys [server]} @state]
    (httpkit/server-stop! server)
    (reset! state nil)))
