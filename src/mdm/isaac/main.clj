(ns mdm.isaac.main
  (:require
    [c3kit.apron.app :as app]
    [c3kit.apron.log :as log]
    [c3kit.apron.util :as util]
    [c3kit.bucket.api :as db]
    [c3kit.wire.destination :as destination]
    [c3kit.wire.routes :as routes]
    [c3kit.wire.websocket :as websocket]
    [mdm.isaac.config :as config]
    [mdm.isaac.init :as init]
    [mdm.isaac.ollama :as ollama]
    [mdm.isaac.think :as think]
    [mdm.isaac.user.web :as user.web]))

(def env (app/service 'c3kit.apron.app/start-env 'c3kit.apron.app/stop-env))

(defn -start-bucket [app] (db/-start-service app (:bucket config/active)))
(def bucket-service (app/service 'mdm.isaac.main/-start-bucket 'c3kit.bucket.api/-stop-service))
(def http-service (app/service 'mdm.isaac.server.http/start 'mdm.isaac.server.http/stop))

;; TODO (isaac-24u) - MDM: moved this start fn and the service into the think namespace.
(defn -start-think [app]
  (think/start-think app ollama/chat {:delay-ms (get config/active :think-delay-ms 5000)}))
(def think-service (app/service 'mdm.isaac.main/-start-think 'mdm.isaac.think/stop-think))

(def all-services [env bucket-service think-service http-service websocket/service])
(def refresh-services [db/service])

(defn maybe-init-dev []
  (when config/development?
    (let [refresh-init (util/resolve-var 'c3kit.apron.refresh/init)]
      (refresh-init refresh-services "mdm.isaac" ['mdm.isaac.server.http 'mdm.isaac.main]))
    (routes/init! {:reload? true})))

(defn init []
  (init/install-legend!)
  (init/configure-api!)
  (destination/configure! (user.web/->AirworthyDestinationAdapter))
  (maybe-init-dev))

(defn start-db [] (app/start! [bucket-service]))
(defn start-all [] (app/start! all-services))
(defn stop-all [] (app/stop! all-services))

(defn -main []
  (log/report "----- STARTING ISAAC -----")
  (log/report "environment: " config/environment)
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-all))
  (.addShutdownHook (Runtime/getRuntime) (Thread. shutdown-agents))
  (start-all))
