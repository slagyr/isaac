(ns mdm.isaac.main
  (:require
    [c3kit.apron.app :as app]
    [c3kit.apron.log :as log]
    [c3kit.bucket.api :as db]
    [mdm.isaac.config :as config]
    [mdm.isaac.ollama :as ollama]
    [mdm.isaac.think :as think]))

(def env (app/service 'c3kit.apron.app/start-env 'c3kit.apron.app/stop-env))

(defn -start-bucket [app] (db/-start-service app (:bucket config/active)))
(def bucket-service (app/service 'mdm.isaac.main/-start-bucket 'c3kit.bucket.api/-stop-service))

(defn -start-think [app]
  (think/start-think app ollama/chat {:delay-ms (get config/active :think-delay-ms 5000)}))
(def think-service (app/service 'mdm.isaac.main/-start-think 'mdm.isaac.think/stop-think))

(def all-services [env bucket-service think-service])

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
