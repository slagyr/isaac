(ns mdm.isaac.main
  (:require
    [c3kit.apron.app :as app]
    [c3kit.apron.log :as log]
    [c3kit.bucket.api :as db]
    [c3kit.wire.api :as api]
    [c3kit.wire.websocket :as websocket]
    [compojure.core :refer [routes GET]]
    [mdm.isaac.config :as config]
    [mdm.isaac.ollama :as ollama]
    [mdm.isaac.think :as think]
    [org.httpkit.server :as httpkit]))

(def env (app/service 'c3kit.apron.app/start-env 'c3kit.apron.app/stop-env))

(defn -start-bucket [app] (db/-start-service app (:bucket config/active)))
(def bucket-service (app/service 'mdm.isaac.main/-start-bucket 'c3kit.bucket.api/-stop-service))

(defn -start-think [app]
  (think/start-think app ollama/chat {:delay-ms (get config/active :think-delay-ms 5000)}))
(def think-service (app/service 'mdm.isaac.main/-start-think 'mdm.isaac.think/stop-think))

;; WebSocket configuration - register handlers before starting websocket service
(api/configure! :ws-handlers 'mdm.isaac.ws/handlers)

;; HTTP routes with WebSocket endpoint
(defn app-routes []
  (routes
    (GET "/ws" request (websocket/handler request))
    (GET "/health" [] {:status 200 :body "OK"})))

(defn -start-http [app]
  (let [port (get config/active :server-port 8080)
        server (httpkit/run-server (app-routes) {:port port})]
    (log/report "HTTP server started on port" port)
    (assoc app :http-server server)))

(defn -stop-http [app]
  (when-let [server (:http-server app)]
    (server)
    (log/report "HTTP server stopped"))
  (dissoc app :http-server))

(def http-service (app/service 'mdm.isaac.main/-start-http 'mdm.isaac.main/-stop-http))

;; Services in order: env -> db -> websocket -> http -> think
(def all-services [env bucket-service websocket/service http-service think-service])

(defn start-db [] (app/start! [bucket-service]))
(defn start-all [] (app/start! all-services))
(defn stop-all [] (app/stop! all-services))

(defn -main []
  (log/report "----- STARTING ISAAC -----")
  (log/report "environment: " config/environment)
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-all))
  (.addShutdownHook (Runtime/getRuntime) (Thread. shutdown-agents))
  (start-all)
  (Thread/sleep 5000))
