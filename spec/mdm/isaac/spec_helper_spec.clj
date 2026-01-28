(ns mdm.isaac.spec-helper-spec
  (:require [mdm.isaac.config :as config]
            [mdm.isaac.spec-helper :refer [with-config]]
            [speclj.core :refer :all]))


(describe "spec-helper"

  (context "with-config"

    (it "temporarily overrides config/active within a context"
      (let [original-embedding (:embedding config/active)]
        (should-not= :test-impl original-embedding)))

    (context "when config is overridden"

      (with-config {:embedding {:impl :test-impl}})

      (it "applies the override"
        (should= {:impl :test-impl} (:embedding config/active))))

    (it "restores original config after context"
      (should-not= :test-impl (get-in config/active [:embedding :impl]))))

  )
