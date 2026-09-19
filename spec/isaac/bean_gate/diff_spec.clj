(ns isaac.bean-gate.diff-spec
  (:require [isaac.bean-gate.core :as sut]
            [speclj.core :refer :all]))

(describe "wip-removal-only?"
  (it "accepts dropping a @wip-only tag line"
    (should (sut/wip-removal-only? {:minus ["  @wip"] :plus []})))
  (it "accepts dropping @wip from a tag line that keeps other tags"
    (should (sut/wip-removal-only? {:minus ["  @wip @slow"] :plus ["  @slow"]})))
  (it "rejects adding a tag while dropping @wip"
    (should-not (sut/wip-removal-only? {:minus ["  @wip"] :plus ["  @slow"]})))
  (it "rejects a step change"
    (should-not (sut/wip-removal-only? {:minus ["    Then a"] :plus ["    Then b"]})))
  (it "rejects a pure addition"
    (should-not (sut/wip-removal-only? {:minus [] :plus ["  @wip"]}))))

(describe "parse-diff"
  (it "reads paths and -U0 hunks, including deleted files"
    (should= [{:path "features/a.feature" :hunks [{:minus ["  @wip"] :plus []}]}
              {:path "features/gone.feature" :hunks [{:minus ["Feature: Gone" "--- not a header"] :plus []}]}]
             (sut/parse-diff (str "diff --git a/features/a.feature b/features/a.feature\n"
                                  "index 1..2 100644\n--- a/features/a.feature\n+++ b/features/a.feature\n"
                                  "@@ -7 +6,0 @@ Feature: A\n-  @wip\n"
                                  "diff --git a/features/gone.feature b/features/gone.feature\n"
                                  "deleted file mode 100644\n--- a/features/gone.feature\n+++ /dev/null\n"
                                  "@@ -1,2 +0,0 @@\n-Feature: Gone\n---- not a header\n")))))
