(ns mdm.isaac.share-spec
  (:require [c3kit.apron.time :as time]
            [c3kit.bucket.api :as db]
            [mdm.isaac.robots :as robots]
            [mdm.isaac.share :as sut]
            [mdm.isaac.ui :as ui]
            [speclj.core :refer :all]))

(def test-embedding (vec (repeat 384 0.1)))

(defn mock-ui [] (ui/->MockUI (atom [])))

(describe "Share"

  (robots/with-kinds :thought)

  (context "create!"

    (it "creates a share thought"
      (let [share (sut/create! "Hello Micah!" test-embedding {:ui (mock-ui)})]
        (should= :share (:type share))
        (should= "Hello Micah!" (:content share))
        (should-be-nil (:read-at share))))

    (it "notifies UI with share message"
      (let [messages (atom [])
            ui (ui/->MockUI messages)
            _ (sut/create! "Hello from Isaac!" test-embedding {:ui ui})]
        (should= 1 (count @messages))
        (should= :info (:type (first @messages)))
        (should-contain "Isaac" (:msg (first @messages)))
        (should-contain "Hello from Isaac!" (:msg (first @messages))))))

  (context "unread"

    (it "returns empty when no shares exist"
      (should= [] (sut/unread)))

    (it "returns only unread shares"
      (let [share1 (sut/create! "First share" test-embedding {:ui (mock-ui)})
            share2 (sut/create! "Second share" test-embedding {:ui (mock-ui)})
            _ (sut/acknowledge! share1)]
        (should= 1 (count (sut/unread)))
        (should= (:id share2) (:id (first (sut/unread))))))

    (it "excludes non-share thoughts"
      (let [_share (sut/create! "A share" test-embedding {:ui (mock-ui)})
            _insight (db/tx {:kind :thought :type :insight :content "An insight" :embedding test-embedding})]
        (should= 1 (count (sut/unread))))))

  (context "acknowledge!"

    (it "marks a share as read with timestamp"
      (let [share (sut/create! "Read me" test-embedding {:ui (mock-ui)})
            before (time/now)
            acknowledged (sut/acknowledge! share)
            after (time/now)]
        (should-not-be-nil (:read-at acknowledged))
        (should (<= (time/millis-since-epoch before) (:read-at acknowledged)))
        (should (>= (time/millis-since-epoch after) (:read-at acknowledged))))))

  )
