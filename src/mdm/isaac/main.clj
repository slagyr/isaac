(ns mdm.isaac.main
  (:require
    [c3kit.apron.app :as app]
    [c3kit.apron.log :as log]
    [c3kit.bucket.api :as db]
    [mdm.isaac.config :as config]))

(def env (app/service 'c3kit.apron.app/start-env 'c3kit.apron.app/stop-env))

(defn -start-bucket [app] (db/-start-service app (:bucket config/active)))
(def bucket-service (app/service 'mdm.isaac.main/-start-bucket 'c3kit.bucket.api/-stop-service))

(def all-services [env bucket-service])

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
