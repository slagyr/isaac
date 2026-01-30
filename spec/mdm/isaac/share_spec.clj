(ns mdm.isaac.share-spec
  (:require [c3kit.apron.time :as time]
            [c3kit.bucket.api :as db]
            [c3kit.bucket.spec-helperc :as helper]
            [mdm.isaac.schema.thought :as schema.thought]
            [mdm.isaac.share :as sut]
            [mdm.isaac.thought :as thought]
            [speclj.core :refer :all]))

(describe "Share"

  (helper/with-schemas [schema.thought/thought])

  (context "create!"

    (it "creates a share thought"
      (let [share (sut/create! "Hello Micah!" (vec (repeat 768 0.1)))]
        (should= :share (:type share))
        (should= "Hello Micah!" (:content share))
        (should-be-nil (:read-at share))))

    (it "prints the share to stdout"
      (let [output (with-out-str
                     (sut/create! "Hello from Isaac!" (vec (repeat 768 0.1))))]
        (should-contain "Isaac" output)
        (should-contain "Hello from Isaac!" output))))

  (context "unread"

    (it "returns empty when no shares exist"
      (should= [] (sut/unread)))

    (it "returns only unread shares"
      (let [share1 (sut/create! "First share" (vec (repeat 768 0.1)))
            share2 (sut/create! "Second share" (vec (repeat 768 0.1)))
            _ (sut/acknowledge! share1)]
        (should= 1 (count (sut/unread)))
        (should= (:id share2) (:id (first (sut/unread))))))

    (it "excludes non-share thoughts"
      (let [_share (sut/create! "A share" (vec (repeat 768 0.1)))
            _insight (db/tx {:kind :thought :type :insight :content "An insight" :embedding (vec (repeat 768 0.1))})]
        (should= 1 (count (sut/unread))))))

  (context "acknowledge!"

    (it "marks a share as read with timestamp"
      (let [share (sut/create! "Read me" (vec (repeat 768 0.1)))
            before (time/now)
            acknowledged (sut/acknowledge! share)
            after (time/now)]
        (should-not-be-nil (:read-at acknowledged))
        (should (<= (time/millis-since-epoch before) (:read-at acknowledged)))
        (should (>= (time/millis-since-epoch after) (:read-at acknowledged))))))

  )
