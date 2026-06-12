(ns isaac.config.check-contributions-spec
  (:require
    [isaac.config.check-contributions :as sut]
    [speclj.core :refer :all]))

(describe "config check-contributions"

  (it "declares server checks for tools and providers (comms validates through the config berth)"
    (should (contains? sut/server :tools))
    (should-not (contains? sut/server :comms))
    (should (contains? sut/server :resolved-providers))))