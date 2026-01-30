(ns mdm.isaac.db
  (:require [c3kit.apron.app :as app]
            [c3kit.bucket.api :as bucket]
            [mdm.isaac.config :as config]))

(defn -start-bucket [app] (bucket/-start-service app (:bucket config/active)))
(def bucket-service (app/service 'mdm.isaac.db/-start 'c3kit.bucket.api/-stop-service))
