(ns isaac.config.check-contributions-spec
  (:require
    [isaac.config.check-contributions :as sut]
    [speclj.core :refer :all]))

(describe "config check-contributions"

  (it "declares server checks for tools comms and providers"
    (should (contains? sut/server :tools))
    (should (contains? sut/server :comms))
    (should (contains? sut/server :resolved-providers))))