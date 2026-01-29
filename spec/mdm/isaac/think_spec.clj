(ns mdm.isaac.think-spec
  (:require [mdm.isaac.spec-helper :refer [with-config]]
            [mdm.isaac.goal :as goal]
            [mdm.isaac.thought :as thought]
            [mdm.isaac.think :as sut]
            [speclj.core :refer :all]))

(describe "Think"

  (with-config {:db {:impl :memory}})
  (before (thought/memory-clear!))

  (context "select-goal"

    (it "returns nil when no active goals exist"
      (should-be-nil (sut/select-goal)))

    (it "returns the highest priority active goal"
      (let [embedding (vec (repeat 768 0.1))
            _low (goal/create! "Low priority" embedding {:priority 5})
            high (goal/create! "High priority" embedding {:priority 1})
            _med (goal/create! "Medium priority" embedding {:priority 3})]
        (should= (:id high) (:id (sut/select-goal)))))

    (it "ignores resolved and abandoned goals"
      (let [embedding (vec (repeat 768 0.1))
            active (goal/create! "Active goal" embedding {:priority 5})
            resolved (goal/create! "Resolved" embedding {:priority 1})
            abandoned (goal/create! "Abandoned" embedding {:priority 1})]
        (goal/resolve! resolved)
        (goal/abandon! abandoned)
        (should= (:id active) (:id (sut/select-goal))))))

  (context "retrieve-context"

    (it "returns empty vector when no similar thoughts exist"
      (let [embedding (vec (repeat 768 0.1))]
        (should= [] (sut/retrieve-context embedding 5))))

    (it "returns similar thoughts excluding goals"
      (let [embedding (vec (repeat 768 0.1))
            insight (thought/save {:kind :thought :type :insight :content "An insight" :embedding embedding})
            _goal (goal/create! "A goal" embedding {:priority 1})
            question (thought/save {:kind :thought :type :question :content "A question" :embedding embedding})
            context (sut/retrieve-context embedding 10)]
        (should= 2 (count context))
        (should (some #(= (:id insight) (:id %)) context))
        (should (some #(= (:id question) (:id %)) context)))))

  (context "build-prompt"

    (it "builds prompt with goal and context"
      (let [goal {:content "Learn Clojure"}
            context [{:content "Clojure is a Lisp"} {:content "Clojure runs on JVM"}]
            prompt (sut/build-prompt goal context)]
        (should-contain "Learn Clojure" prompt)
        (should-contain "Clojure is a Lisp" prompt)
        (should-contain "three fundamental laws" prompt))))

  (context "parse-response"

    (it "extracts thoughts from LLM response"
      (let [response "INSIGHT: I understand now that Clojure is functional.\nQUESTION: What are macros?"
            thoughts (sut/parse-response response (vec (repeat 768 0.1)))]
        (should= 2 (count thoughts))
        (should= :insight (:type (first thoughts)))
        (should-contain "functional" (:content (first thoughts)))
        (should= :question (:type (second thoughts))))))

  (context "think-once!"

    (it "does nothing when no active goals"
      (should-be-nil (sut/think-once! identity)))

    (it "processes a goal and creates new thoughts"
      (let [embedding (vec (repeat 768 0.1))
            mock-llm (fn [_] "INSIGHT: Testing works!")
            _goal (goal/create! "Test goal" embedding {:priority 1})]
        (sut/think-once! mock-llm)
        (let [insights (thought/find-by-type :insight)]
          (should= 1 (count insights))
          (should-contain "Testing works" (:content (first insights)))))))

  (context "start! / stop!"

    (it "stop! sets running? to false"
      (sut/stop!)
      (should= false @sut/running?))

    (it "start! runs iterations until stop! is called"
      (let [embedding (vec (repeat 768 0.1))
            call-count (atom 0)
            mock-llm (fn [_]
                       (swap! call-count inc)
                       (when (>= @call-count 3)
                         (sut/stop!))
                       "INSIGHT: Thinking...")
            _goal (goal/create! "Test goal" embedding {:priority 1})]
        (sut/start! mock-llm {:delay-ms 0})
        (should= 3 @call-count)))

    (it "respects delay-ms between iterations"
      (let [embedding (vec (repeat 768 0.1))
            timestamps (atom [])
            mock-llm (fn [_]
                       (swap! timestamps conj (System/currentTimeMillis))
                       (when (>= (count @timestamps) 3)
                         (sut/stop!))
                       "INSIGHT: Thinking...")
            _goal (goal/create! "Test goal" embedding {:priority 1})]
        (sut/start! mock-llm {:delay-ms 50})
        (let [delays (map - (rest @timestamps) @timestamps)]
          (should (every? #(>= % 40) delays))))))  ;; Allow some timing slack

  )
