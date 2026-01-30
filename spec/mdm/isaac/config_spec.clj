(ns mdm.isaac.config-spec
  (:require [mdm.isaac.config :as config]
            [speclj.core :refer :all]))

(describe "config"

  (it "bucket"
    (should= "isaac-dev" (-> config/development :bucket :dbname))
    (should= "isaac-staging" (-> config/staging :bucket :dbname))
    (should= "isaac-prod" (-> config/production :bucket :dbname)))

)
