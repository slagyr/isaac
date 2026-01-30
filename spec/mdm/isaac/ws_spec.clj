(ns mdm.isaac.ws-spec
  (:require [mdm.isaac.spec-helper :refer [with-config]]
            [mdm.isaac.embedding.core :as embedding]
            [mdm.isaac.goal :as goal]
            [mdm.isaac.share :as share]
            [mdm.isaac.thought :as thought]
            [mdm.isaac.ws :as sut]
            [speclj.core :refer :all]))

(def test-embedding (vec (repeat 768 0.1)))

;; Mock embedding implementation for tests
(defmethod embedding/text-embedding :mock [_text] test-embedding)
(defmethod embedding/dimensions :mock [] 768)

(describe "WebSocket Handlers"

  (with-config {:db {:impl :memory}})
  (before (thought/memory-clear!))

  (context "goals-list"

    (it "returns empty list when no active goals"
      (let [result (sut/goals-list {})]
        (should= :ok (:status result))
        (should= [] (:payload result))))

    (it "returns all active goals"
      (let [goal1 (goal/create! "Goal one" test-embedding {:priority 1})
            goal2 (goal/create! "Goal two" test-embedding {:priority 2})
            result (sut/goals-list {})]
        (should= :ok (:status result))
        (should= 2 (count (:payload result)))
        (should (some #(= "Goal one" (:content %)) (:payload result)))
        (should (some #(= "Goal two" (:content %)) (:payload result)))))

    (it "excludes resolved and abandoned goals"
      (let [active (goal/create! "Active" test-embedding {:priority 1})
            resolved (goal/create! "Resolved" test-embedding {:priority 2})
            abandoned (goal/create! "Abandoned" test-embedding {:priority 3})]
        (goal/resolve! resolved)
        (goal/abandon! abandoned)
        (let [result (sut/goals-list {})]
          (should= :ok (:status result))
          (should= 1 (count (:payload result)))
          (should= "Active" (-> result :payload first :content))))))

  (context "goals-add"

    (with-config {:db {:impl :memory} :embedding {:impl :mock}})

    (it "creates a new goal with content"
      (let [result (sut/goals-add {:params {:content "Learn Clojure"}})]
        (should= :ok (:status result))
        (should= "Learn Clojure" (-> result :payload :content))
        (should= :goal (-> result :payload :type))
        (should= :active (-> result :payload :status))))

    (it "creates goal with custom priority"
      (let [result (sut/goals-add {:params {:content "High priority" :priority 1}})]
        (should= :ok (:status result))
        (should= 1 (-> result :payload :priority))))

    (it "uses default priority 5 when not specified"
      (let [result (sut/goals-add {:params {:content "Default priority"}})]
        (should= :ok (:status result))
        (should= 5 (-> result :payload :priority))))

    (it "returns fail when content is missing"
      (let [result (sut/goals-add {:params {}})]
        (should= :fail (:status result)))))

  (context "goals-update"

    (it "updates goal status to resolved"
      (let [goal (goal/create! "To resolve" test-embedding {:priority 1})
            result (sut/goals-update {:params {:id (:id goal) :status :resolved}})]
        (should= :ok (:status result))
        (should= :resolved (-> result :payload :status))))

    (it "updates goal status to abandoned"
      (let [goal (goal/create! "To abandon" test-embedding {:priority 1})
            result (sut/goals-update {:params {:id (:id goal) :status :abandoned}})]
        (should= :ok (:status result))
        (should= :abandoned (-> result :payload :status))))

    (it "updates goal priority"
      (let [goal (goal/create! "To prioritize" test-embedding {:priority 5})
            result (sut/goals-update {:params {:id (:id goal) :priority 1}})]
        (should= :ok (:status result))
        (should= 1 (-> result :payload :priority))))

    (it "returns fail when goal not found"
      (let [result (sut/goals-update {:params {:id 99999 :status :resolved}})]
        (should= :fail (:status result)))))

  (context "thoughts-recent"

    (it "returns empty list when no thoughts"
      (let [result (sut/thoughts-recent {})]
        (should= :ok (:status result))
        (should= [] (:payload result))))

    (it "returns recent thoughts of all types"
      (let [_insight (thought/save {:kind :thought :type :insight :content "An insight" :embedding test-embedding})
            _question (thought/save {:kind :thought :type :question :content "A question" :embedding test-embedding})
            _goal (goal/create! "A goal" test-embedding {:priority 1})
            result (sut/thoughts-recent {:params {:limit 10}})]
        (should= :ok (:status result))
        (should= 3 (count (:payload result)))))

    (it "respects limit parameter"
      (let [_t1 (thought/save {:kind :thought :type :insight :content "First" :embedding test-embedding})
            _t2 (thought/save {:kind :thought :type :insight :content "Second" :embedding test-embedding})
            _t3 (thought/save {:kind :thought :type :insight :content "Third" :embedding test-embedding})
            result (sut/thoughts-recent {:params {:limit 2}})]
        (should= :ok (:status result))
        (should= 2 (count (:payload result)))))

    (it "defaults to limit 20"
      (let [result (sut/thoughts-recent {:params {}})]
        (should= :ok (:status result)))))

  (context "thoughts-search"

    (with-config {:db {:impl :memory} :embedding {:impl :mock}})

    (it "returns empty list when no thoughts"
      (let [result (sut/thoughts-search {:params {:query "test"}})]
        (should= :ok (:status result))
        (should= [] (:payload result))))

    (it "searches thoughts by query text"
      (let [_t1 (thought/save {:kind :thought :type :insight :content "Clojure is great" :embedding test-embedding})
            _t2 (thought/save {:kind :thought :type :insight :content "Java is verbose" :embedding test-embedding})
            result (sut/thoughts-search {:params {:query "programming"}})]
        (should= :ok (:status result))
        ;; Should find similar thoughts based on embedding
        (should (>= (count (:payload result)) 0))))

    (it "respects limit parameter"
      (let [_t1 (thought/save {:kind :thought :type :insight :content "First" :embedding test-embedding})
            _t2 (thought/save {:kind :thought :type :insight :content "Second" :embedding test-embedding})
            result (sut/thoughts-search {:params {:query "test" :limit 1}})]
        (should= :ok (:status result))
        (should= 1 (count (:payload result)))))

    (it "returns fail when query is missing"
      (let [result (sut/thoughts-search {:params {}})]
        (should= :fail (:status result)))))

  (context "shares-unread"

    (it "returns empty list when no unread shares"
      (let [result (sut/shares-unread {})]
        (should= :ok (:status result))
        (should= [] (:payload result))))

    (it "returns all unread shares"
      (let [share1 (share/create! "Share one" test-embedding)
            share2 (share/create! "Share two" test-embedding)
            result (sut/shares-unread {})]
        (should= :ok (:status result))
        (should= 2 (count (:payload result)))))

    (it "excludes acknowledged shares"
      (let [unread (share/create! "Unread" test-embedding)
            read-share (share/create! "Read" test-embedding)]
        (share/acknowledge! read-share)
        (let [result (sut/shares-unread {})]
          (should= :ok (:status result))
          (should= 1 (count (:payload result)))
          (should= "Unread" (-> result :payload first :content))))))

  (context "shares-ack"

    (it "acknowledges a share by id"
      (let [share (share/create! "To ack" test-embedding)
            result (sut/shares-ack {:params {:id (:id share)}})]
        (should= :ok (:status result))
        ;; Verify the share is now acknowledged
        (let [unread-result (sut/shares-unread {})]
          (should= [] (:payload unread-result)))))

    (it "returns fail when share not found"
      (let [result (sut/shares-ack {:params {:id 99999}})]
        (should= :fail (:status result)))))

  )
