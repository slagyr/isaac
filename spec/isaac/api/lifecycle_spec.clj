(ns isaac.api.lifecycle-spec
  (:require
    [isaac.api.lifecycle :as sut]
    [isaac.lifecycle :as impl]
    [speclj.core :refer :all]))

(describe "isaac.api.lifecycle"

  (it "Lifecycle is the same protocol var as isaac.lifecycle/Lifecycle"
    (should= impl/Lifecycle sut/Lifecycle))

  (it "a record implementing sut/Lifecycle satisfies impl/Lifecycle"
    (let [r (reify sut/Lifecycle
              (on-startup! [_ _] :started)
              (on-config-change! [_ _ _] :changed))]
      (should (satisfies? impl/Lifecycle r))
      (should (satisfies? sut/Lifecycle r)))))
