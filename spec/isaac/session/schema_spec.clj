(ns isaac.session.schema-spec
  (:require
    [c3kit.apron.schema :as schema]
    [isaac.session.schema :as sut]
    [speclj.core :refer :all]))

(describe "Session schema"

  (it "coerces legacy camelCase keys on read"
    (should= {:id          "alpha"
              :name        "Alpha"
              :created-at  "2026-05-08T10:00:00"
              :chat-type   "direct"
              :updated-at  "2026-05-08T10:00:01"}
             (select-keys (sut/conform-read {:id        "alpha"
                                             :name      "Alpha"
                                             :createdAt "2026-05-08T10:00:00"
                                             :chatType  "direct"
                                             :updated-at "2026-05-08T10:00:01"})
                          [:id :name :created-at :chat-type :updated-at])))

  (it "drops unknown keys on read"
    (should-not (contains? (sut/conform-read {:id "alpha" :name "Alpha" :mystery true}) :mystery)))

  (it "requires id and name on write"
    (should-throw clojure.lang.ExceptionInfo
                  (sut/conform! {:id "alpha"})))

  (it "accepts the observed session metadata fields"
    (should-not-throw
      (sut/conform! {:id                  "alpha"
                     :key                 "alpha"
                     :name                "Alpha"
                     :sessionId           "hdr-1"
                     :session-file        "alpha.jsonl"
                     :origin              {:kind :cli}
                     :crew                "main"
                     :model               "echo"
                     :provider            "grover"
                     :channel             "cli"
                     :chat-type           "direct"
                     :cwd                 "/tmp/alpha"
                     :created-at          "2026-05-08T10:00:00"
                     :updated-at          "2026-05-08T10:00:01"
                     :compaction-count    0
                     :compaction-disabled false
                     :compaction          {:consecutive-failures 0}
                     :input-tokens        1
                     :output-tokens       2
                     :total-tokens        3
                     :last-input-tokens   1
                     :cache-read          4
                     :cache-write         5})))

  (it "returns readable schema errors for invalid read data"
    (let [result (sut/conform-read {:id "alpha" :name nil})]
      (should (schema/error? result))
      (should= "must be present" (get-in (schema/message-map result) [:name])))))
