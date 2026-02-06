(ns mdm.isaac.config-spec
  (:require [mdm.isaac.config :as config]
            [speclj.core :refer :all]))

(describe "config"

  (it "bucket"
    (should= "isaac-dev" (-> config/development :bucket :dbname))
    (should= "isaac-staging" (-> config/staging :bucket :dbname))
    (should= "isaac-prod" (-> config/production :bucket :dbname)))

  (it "ws-url builds WebSocket URL from host and port"
    (should= "ws://localhost:8600/user/ws" (config/ws-url)))

  (it "http-url builds HTTP URL from host and port"
    (should= "http://localhost:8600" (config/http-url)))

)
