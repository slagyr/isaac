(ns isaac.api.comm-spec
  (:require
    [isaac.api.comm :as sut]
    [isaac.comm :as impl]
    [speclj.core :refer :all]))

(describe "isaac.api.comm"

  (it "Comm re-exports the same named protocol"
    (should= (:name impl/Comm) (:name sut/Comm))
    (should= (set (keys (:sigs impl/Comm))) (set (keys (:sigs sut/Comm)))))

  (it "a type implementing sut/Comm satisfies sut/Comm"
    (let [r (reify sut/Comm
              (on-turn-start [_ _ _] nil)
              (on-text-chunk [_ _ _] nil)
              (on-tool-call [_ _ _] nil)
              (on-tool-cancel [_ _ _] nil)
              (on-tool-result [_ _ _ _] nil)
              (on-compaction-start [_ _ _] nil)
              (on-compaction-success [_ _ _] nil)
              (on-compaction-failure [_ _ _] nil)
              (on-compaction-disabled [_ _ _] nil)
              (on-turn-end [_ _ _] nil)
              (on-error [_ _ _] nil))]
      (should (satisfies? sut/Comm r)))))
