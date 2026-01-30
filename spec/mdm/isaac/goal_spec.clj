(ns mdm.isaac.goal-spec
  (:require [c3kit.bucket.spec-helperc :as helper]
            [mdm.isaac.goal :as sut]
            [mdm.isaac.schema.thought :as schema.thought]
            [mdm.isaac.thought :as thought]
            [speclj.core :refer :all]))

(describe "Goal"

  (helper/with-schemas [schema.thought/thought])

  (it "creates a goal with :type :goal"
    (let [goal (sut/create! "Learn Clojure" (vec (repeat 768 0.1)))]
      (should= :goal (:type goal))
      (should= "Learn Clojure" (:content goal))
      (should= :active (:status goal))))

  (it "creates goal with specified priority"
    (let [goal (sut/create! "Urgent task" (vec (repeat 768 0.1)) {:priority 1})]
      (should= 1 (:priority goal))))

  (it "defaults priority to 5"
    (let [goal (sut/create! "Normal task" (vec (repeat 768 0.1)))]
      (should= 5 (:priority goal))))

  (it "finds all active goals"
    ;(thought/memory-clear!)
    (sut/create! "Goal 1" (vec (repeat 768 0.1)))
    (sut/create! "Goal 2" (vec (repeat 768 0.2)))
    (let [goals (sut/find-active)]
      (should= 2 (count goals))
      (should (every? #(= :active (:status %)) goals))))

  (it "resolves a goal"
    (let [goal     (sut/create! "Finish project" (vec (repeat 768 0.1)))
          resolved (sut/resolve! goal)]
      (should= :resolved (:status resolved))))

  (it "abandons a goal"
    (let [goal      (sut/create! "Maybe later" (vec (repeat 768 0.1)))
          abandoned (sut/abandon! goal)]
      (should= :abandoned (:status abandoned))))

  (it "find-active excludes resolved and abandoned goals"
    ;(thought/memory-clear!)
    (let [g1 (sut/create! "Active goal" (vec (repeat 768 0.1)))
          g2 (sut/create! "Will resolve" (vec (repeat 768 0.2)))
          g3 (sut/create! "Will abandon" (vec (repeat 768 0.3)))]
      (sut/resolve! g2)
      (sut/abandon! g3)
      (let [active (sut/find-active)]
        (should= 1 (count active))
        (should= (:id g1) (:id (first active))))))

  )

