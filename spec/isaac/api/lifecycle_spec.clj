(ns isaac.api.lifecycle-spec
  (:require
    [isaac.api.lifecycle :as sut]
    [isaac.lifecycle :as impl]
    [speclj.core :refer :all]))

(describe "isaac.api.lifecycle"

  (it "Lifecycle re-exports the same named protocol"
    (should= (:name impl/Lifecycle) (:name sut/Lifecycle))
    (should= (set (keys (:sigs impl/Lifecycle))) (set (keys (:sigs sut/Lifecycle)))))

  (it "a type implementing sut/Lifecycle satisfies sut/Lifecycle"
    (let [r (reify sut/Lifecycle
              (on-startup! [_ _] :started)
              (on-config-change! [_ _ _] :changed))]
      (should (satisfies? sut/Lifecycle r)))))
