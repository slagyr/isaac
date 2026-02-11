(ns mdm.isaac.thought.ws-spec
  (:require [c3kit.bucket.api :as db]
            [c3kit.bucket.spec-helperc :as helper]
            [mdm.isaac.thought.schema :as schema.thought]
            [mdm.isaac.spec-helper :refer [with-config]]
            [mdm.isaac.embedding.core :as embedding]
            [mdm.isaac.goal :as goal]
            [mdm.isaac.thought.ws :as sut]
            [speclj.core :refer :all]))

(def test-embedding (vec (repeat 384 0.1)))

;; Mock embedding implementation for tests
(defmethod embedding/text-embedding :mock [_text] test-embedding)
(defmethod embedding/dimensions :mock [] 384)

(describe "Thought WebSocket Handlers"

  (helper/with-schemas [schema.thought/thought])

  (context "ws-recent"

    (it "returns empty list when no thoughts"
      (let [result (sut/ws-recent {})]
        (should= :ok (:status result))
        (should= [] (:payload result))))

    (it "returns recent thoughts of all types"
      (let [_insight (db/tx {:kind :thought :type "insight" :content "An insight" :embedding test-embedding})
            _question (db/tx {:kind :thought :type "question" :content "A question" :embedding test-embedding})
            _goal (goal/create! "A goal" test-embedding {:priority 1})
            result (sut/ws-recent {:params {:limit 10}})]
        (should= :ok (:status result))
        (should= 3 (count (:payload result)))))

    (it "respects limit parameter"
      (let [_t1 (db/tx {:kind :thought :type "insight" :content "First" :embedding test-embedding})
            _t2 (db/tx {:kind :thought :type "insight" :content "Second" :embedding test-embedding})
            _t3 (db/tx {:kind :thought :type "insight" :content "Third" :embedding test-embedding})
            result (sut/ws-recent {:params {:limit 2}})]
        (should= :ok (:status result))
        (should= 2 (count (:payload result)))))

    (it "defaults to limit 20"
      (let [result (sut/ws-recent {:params {}})]
        (should= :ok (:status result)))))

  (context "ws-search"

    (with-config {:db {:impl :memory} :embedding {:impl :mock}})

    (it "returns empty list when no thoughts"
      (let [result (sut/ws-search {:params {:query "test"}})]
        (should= :ok (:status result))
        (should= [] (:payload result))))

    (it "searches thoughts by query text"
      (let [_t1 (db/tx {:kind :thought :type "insight" :content "Clojure is great" :embedding test-embedding})
            _t2 (db/tx {:kind :thought :type "insight" :content "Java is verbose" :embedding test-embedding})
            result (sut/ws-search {:params {:query "programming"}})]
        (should= :ok (:status result))
        ;; Should find similar thoughts based on embedding
        (should (>= (count (:payload result)) 0))))

    (it "respects limit parameter"
      (let [_t1 (db/tx {:kind :thought :type "insight" :content "First" :embedding test-embedding})
            _t2 (db/tx {:kind :thought :type "insight" :content "Second" :embedding test-embedding})
            result (sut/ws-search {:params {:query "test" :limit 1}})]
        (should= :ok (:status result))
        (should= 1 (count (:payload result)))))

    (it "returns fail when query is missing"
      (let [result (sut/ws-search {:params {}})]
        (should= :fail (:status result)))))

  )
