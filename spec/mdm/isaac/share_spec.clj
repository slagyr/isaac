(ns mdm.isaac.share-spec
  (:require [mdm.isaac.spec-helper :refer [with-config]]
            [mdm.isaac.share :as sut]
            [mdm.isaac.thought :as thought]
            [speclj.core :refer :all]))

(describe "Share"

  (with-config {:db {:impl :memory}})
  (before (thought/memory-clear!))

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
            _insight (thought/save {:kind :thought :type :insight :content "An insight" :embedding (vec (repeat 768 0.1))})]
        (should= 1 (count (sut/unread))))))

  (context "acknowledge!"

    (it "marks a share as read with timestamp"
      (let [share (sut/create! "Read me" (vec (repeat 768 0.1)))
            before (java.time.Instant/now)
            acknowledged (sut/acknowledge! share)
            after (java.time.Instant/now)]
        (should-not-be-nil (:read-at acknowledged))
        (should (<= (.toEpochMilli before) (:read-at acknowledged)))
        (should (>= (.toEpochMilli after) (:read-at acknowledged))))))

  )
