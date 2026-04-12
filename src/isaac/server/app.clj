(ns isaac.server.app
  (:require
    [c3kit.apron.refresh :as refresh]
    [isaac.logger :as log]
    [isaac.server.http :as http]
    [org.httpkit.server :as httpkit]))

(defonce ^:private state (atom nil))

(defn running? []
  (some? @state))

(defn- dev-handler []
  (refresh/init refresh/services "isaac" [])
  (let [refreshing (refresh/refresh-handler 'isaac.server.http/root-handler)
        scanning   (fn [request]
                     (log/debug :server/dev-reload-scan
                                :method (:request-method request)
                                :uri (:uri request))
                     (refreshing request))]
    (http/wrap-logging scanning)))

(defn start! [opts]
  (when (running?) (httpkit/server-stop! (:server @state)))
  (let [port    (or (:port opts) 6674) ;; 6.674 is Newton's gravitational constant
         host    (or (:host opts) "0.0.0.0")
         dev?    (true? (:dev opts))
         handler (if dev? (dev-handler) (http/create-handler opts))
         server  (httpkit/run-server handler {:port port :ip host :legacy-return-value? false})
         actual  (httpkit/server-port server)]
    (when dev?
      (log/info :server/dev-mode-enabled :host host :port actual))
    (reset! state {:server server :port actual :host host})
    {:port actual :host host}))

(defn stop! []
  (when-let [{:keys [server]} @state]
    (httpkit/server-stop! server)
    (reset! state nil)))
