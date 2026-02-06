(ns mdm.isaac.share.ws-spec
  (:require [c3kit.bucket.spec-helperc :as helper]
            [mdm.isaac.thought.schema :as schema.thought]
            [mdm.isaac.embedding.core :as embedding]
            [mdm.isaac.share :as share]
            [mdm.isaac.share.ws :as sut]
            [mdm.isaac.ui :as ui]
            [speclj.core :refer :all]))

(def test-embedding (vec (repeat 384 0.1)))

;; Mock UI to suppress test output
(defn mock-ui [] (ui/->MockUI (atom [])))

;; Mock embedding implementation for tests
(defmethod embedding/text-embedding :mock [_text] test-embedding)
(defmethod embedding/dimensions :mock [] 384)

(describe "Share WebSocket Handlers"

  (helper/with-schemas [schema.thought/thought])

  (context "ws-unread"

    (it "returns empty list when no unread shares"
      (let [result (sut/ws-unread {})]
        (should= :ok (:status result))
        (should= [] (:payload result))))

    (it "returns all unread shares"
      (let [_share1 (share/create! "Share one" test-embedding {:ui (mock-ui)})
            _share2 (share/create! "Share two" test-embedding {:ui (mock-ui)})
            result (sut/ws-unread {})]
        (should= :ok (:status result))
        (should= 2 (count (:payload result)))))

    (it "excludes acknowledged shares"
      (let [_unread (share/create! "Unread" test-embedding {:ui (mock-ui)})
            read-share (share/create! "Read" test-embedding {:ui (mock-ui)})]
        (share/acknowledge! read-share)
        (let [result (sut/ws-unread {})]
          (should= :ok (:status result))
          (should= 1 (count (:payload result)))
          (should= "Unread" (-> result :payload first :content))))))

  (context "ws-ack"

    (it "acknowledges a share by id"
      (let [share (share/create! "To ack" test-embedding {:ui (mock-ui)})
            result (sut/ws-ack {:params {:id (:id share)}})]
        (should= :ok (:status result))
        ;; Verify the share is now acknowledged
        (let [unread-result (sut/ws-unread {})]
          (should= [] (:payload unread-result)))))

    (it "returns fail when share not found"
      (let [result (sut/ws-ack {:params {:id 99999}})]
        (should= :fail (:status result)))))

  )
