(ns isaac.config.schema-contributions-spec
  (:require
    [isaac.config.schema-contributions :as sut]
    [speclj.core :refer :all]))

(describe "config schema-contributions"

  (it "declares server schema fragments for crew cron and defaults"
    (should (contains? sut/server :crew))
    (should (contains? sut/server :cron))
    (should (contains? sut/server :defaults))
    (should= "crew" (get-in sut/server [:crew :entity-dir]))))