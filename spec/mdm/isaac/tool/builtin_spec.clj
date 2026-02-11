(ns mdm.isaac.tool.builtin-spec
  (:require [c3kit.bucket.api :as db]
            [c3kit.bucket.spec-helperc :as helper]
            [mdm.isaac.embedding.core :as embedding]
            [mdm.isaac.goal.core :as goal]
            [mdm.isaac.thought.schema :as schema.thought]
            [mdm.isaac.tool.builtin :as sut]
            [mdm.isaac.tool.core :as tool]
            [speclj.core :refer :all]))

(defn mock-embedding [_text]
  (vec (repeat 384 0.1)))

(describe "Built-in Tools"

  (helper/with-schemas [schema.thought/thought])

  (around [it]
    (with-redefs [embedding/text-embedding mock-embedding]
      (tool/clear!)
      (sut/register-all!)
      (it)))

  (context "create-goal tool"

    (it "is registered"
      (should-not-be-nil (tool/get-tool :create-goal)))

    (it "creates a goal with content"
      (let [result (tool/execute! :create-goal {:content "Learn Clojure"})]
        (should= :ok (:status result))
        (should= "Learn Clojure" (-> result :goal :content))
        (should= "goal" (-> result :goal :type))))

    (it "creates a goal with priority"
      (let [result (tool/execute! :create-goal {:content "Important goal" :priority 1})]
        (should= 1 (-> result :goal :priority))))

    (it "fails without content"
      (let [result (tool/execute! :create-goal {})]
        (should= :error (:status result)))))

  (context "list-goals tool"

    (it "is registered"
      (should-not-be-nil (tool/get-tool :list-goals)))

    (it "returns empty list when no goals"
      (let [result (tool/execute! :list-goals {})]
        (should= :ok (:status result))
        (should= [] (:goals result))))

    (it "returns active goals"
      (goal/create! "Goal 1" (mock-embedding "Goal 1") {:priority 1})
      (goal/create! "Goal 2" (mock-embedding "Goal 2") {:priority 2})
      (let [result (tool/execute! :list-goals {})]
        (should= :ok (:status result))
        (should= 2 (count (:goals result))))))

  (context "update-goal tool"

    (it "is registered"
      (should-not-be-nil (tool/get-tool :update-goal)))

    (it "updates goal status to resolved"
      (let [created (goal/create! "Test goal" (mock-embedding "Test goal") {})
            result (tool/execute! :update-goal {:id (:id created) :status "resolved"})]
        (should= :ok (:status result))
        (should= "resolved" (-> result :goal :status))))

    (it "fails for nonexistent goal"
      (let [result (tool/execute! :update-goal {:id 99999 :status "resolved"})]
        (should= :error (:status result)))))

  (context "search-thoughts tool"

    (it "is registered"
      (should-not-be-nil (tool/get-tool :search-thoughts)))

    (it "returns matching thoughts"
      (db/tx {:kind :thought :type "insight" :content "Clojure insight" :embedding (mock-embedding "")})
      (let [result (tool/execute! :search-thoughts {:query "Clojure"})]
        (should= :ok (:status result))
        (should (seq (:thoughts result))))))

  (context "create-thought tool"

    (it "is registered"
      (should-not-be-nil (tool/get-tool :create-thought)))

    (it "creates an insight"
      (let [result (tool/execute! :create-thought {:content "A new insight" :type "insight"})]
        (should= :ok (:status result))
        (should= "insight" (-> result :thought :type))
        (should= "A new insight" (-> result :thought :content))))

    (it "defaults to 'thought' type"
      (let [result (tool/execute! :create-thought {:content "Generic thought"})]
        (should= "thought" (-> result :thought :type)))))

  )
