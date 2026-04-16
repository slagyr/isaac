(ns isaac.session.compaction-spec
  (:require
    [isaac.session.compaction :as sut]
    [speclj.core :refer :all]))

(describe "Session Compaction"

  (describe "default-threshold"
    (it "uses max of window-50k and 80 percent"
      (should= 80 (sut/default-threshold 100))
      (should= 6553 (sut/default-threshold 8192))
      (should= 26214 (sut/default-threshold 32768))
      (should= 102400 (sut/default-threshold 128000))
      (should= 998576 (sut/default-threshold 1048576))))

  (describe "default-tail"
    (it "uses max of window-150k and 70 percent"
      (should= 70 (sut/default-tail 100))
      (should= 5734 (sut/default-tail 8192))
      (should= 22937 (sut/default-tail 32768))
      (should= 89600 (sut/default-tail 128000))
      (should= 898576 (sut/default-tail 1048576))))

  (describe "resolve-config"
    (it "defaults to rubberband with derived threshold and tail"
      (should= {:async? false :strategy :rubberband :tail 22937 :threshold 26214}
               (sut/resolve-config {} 32768)))

    (it "merges session overrides"
      (should= {:async? false :strategy :slinky :tail 80 :threshold 160}
               (sut/resolve-config {:compaction {:strategy :slinky :threshold 160 :tail 80}} 200)))

    (it "coerces string strategy values"
      (should= {:async? false :strategy :slinky :tail 80 :threshold 160}
               (sut/resolve-config {:compaction {:strategy "slinky" :threshold 160 :tail 80}} 200))))

  (describe "should-compact?"
    (it "uses strategy threshold rather than a hard-coded 90 percent"
      (should-not (sut/should-compact? {:totalTokens 159 :compaction {:strategy :slinky :threshold 160 :tail 80}} 200))
      (should (sut/should-compact? {:totalTokens 160 :compaction {:strategy :slinky :threshold 160 :tail 80}} 200))))

  (describe "sliding compaction target"
    (it "for rubberband compacts the whole effective history"
      (let [entries [{:id "m1" :tokens 40}
                     {:id "m2" :tokens 40}
                     {:id "m3" :tokens 40}
                     {:id "m4" :tokens 50}]]
        (should= {:compact-count 4 :first-kept-entry-id nil :tokens-before 170}
                 (sut/compaction-target entries {:strategy :rubberband :tail 80 :threshold 160}))))

    (it "for slinky compacts only enough oldest entries to preserve the tail"
      (let [entries [{:id "m1" :tokens 40}
                     {:id "m2" :tokens 40}
                     {:id "m3" :tokens 40}
                     {:id "m4" :tokens 50}]]
        (should= {:compact-count 2 :first-kept-entry-id "m3" :tokens-before 80}
                 (sut/compaction-target entries {:strategy :slinky :tail 80 :threshold 160}))))))
