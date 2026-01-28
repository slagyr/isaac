(ns mdm.isaac.main
  (:require
    [c3kit.apron.app :as app]
    [c3kit.apron.log :as log]
    [c3kit.bucket.api :as db]
    [c3kit.wire.websocket :as websocket]
    [mdm.isaac.config :as config]))

(defn start-env [app] (app/start-env "c3.env" "C3_ENV" app))

(def env (app/service 'mdm.isaac.main/start-env 'c3kit.apron.app/stop-env))

(def all-services [env db/service])

(defn start-db [] (app/start! [db/service]))
(defn start-all [] (app/start! all-services))
(defn stop-all [] (app/stop! all-services))

(defn -main []
  (log/report "----- STARTING ISAAC -----")
  (log/report "environment: " config/environment)
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-all))
  (.addShutdownHook (Runtime/getRuntime) (Thread. shutdown-agents))
  (start-all))
