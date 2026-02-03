(ns mdm.isaac.goal.ws-spec
  (:require [c3kit.bucket.spec-helperc :as helper]
            [mdm.isaac.thought.schema :as schema.thought]
            [mdm.isaac.spec-helper :refer [with-config]]
            [mdm.isaac.embedding.core :as embedding]
            [mdm.isaac.goal :as goal]
            [mdm.isaac.goal.ws :as sut]
            [speclj.core :refer :all]))

(def test-embedding (vec (repeat 768 0.1)))

;; Mock embedding implementation for tests
(defmethod embedding/text-embedding :mock [_text] test-embedding)
(defmethod embedding/dimensions :mock [] 768)

(describe "Goal WebSocket Handlers"

  (helper/with-schemas [schema.thought/thought])

  (context "ws-list"

    (it "returns empty list when no active goals"
      (let [result (sut/ws-list {})]
        (should= :ok (:status result))
        (should= [] (:payload result))))

    (it "returns all active goals"
      (let [goal1 (goal/create! "Goal one" test-embedding {:priority 1})
            goal2 (goal/create! "Goal two" test-embedding {:priority 2})
            result (sut/ws-list {})]
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
        (let [result (sut/ws-list {})]
          (should= :ok (:status result))
          (should= 1 (count (:payload result)))
          (should= "Active" (-> result :payload first :content))))))

  (context "ws-add"

    (with-config {:db {:impl :memory} :embedding {:impl :mock}})

    (it "creates a new goal with content"
      (let [result (sut/ws-add {:params {:content "Learn Clojure"}})]
        (should= :ok (:status result))
        (should= "Learn Clojure" (-> result :payload :content))
        (should= :goal (-> result :payload :type))
        (should= :active (-> result :payload :status))))

    (it "creates goal with custom priority"
      (let [result (sut/ws-add {:params {:content "High priority" :priority 1}})]
        (should= :ok (:status result))
        (should= 1 (-> result :payload :priority))))

    (it "uses default priority 5 when not specified"
      (let [result (sut/ws-add {:params {:content "Default priority"}})]
        (should= :ok (:status result))
        (should= 5 (-> result :payload :priority))))

    (it "returns fail when content is missing"
      (let [result (sut/ws-add {:params {}})]
        (should= :fail (:status result)))))

  (context "ws-update"

    (it "updates goal status to resolved"
      (let [goal (goal/create! "To resolve" test-embedding {:priority 1})
            result (sut/ws-update {:params {:id (:id goal) :status :resolved}})]
        (should= :ok (:status result))
        (should= :resolved (-> result :payload :status))))

    (it "updates goal status to abandoned"
      (let [goal (goal/create! "To abandon" test-embedding {:priority 1})
            result (sut/ws-update {:params {:id (:id goal) :status :abandoned}})]
        (should= :ok (:status result))
        (should= :abandoned (-> result :payload :status))))

    (it "updates goal priority"
      (let [goal (goal/create! "To prioritize" test-embedding {:priority 5})
            result (sut/ws-update {:params {:id (:id goal) :priority 1}})]
        (should= :ok (:status result))
        (should= 1 (-> result :payload :priority))))

    (it "returns fail when goal not found"
      (let [result (sut/ws-update {:params {:id 99999 :status :resolved}})]
        (should= :fail (:status result)))))

  )
