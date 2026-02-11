(ns mdm.isaac.think-spec
  (:require [c3kit.bucket.api :as db]
            [c3kit.bucket.spec-helperc :as helper]
            [mdm.isaac.thought.schema :as schema.thought]
            [mdm.isaac.setting.schema :as schema.setting]
            [mdm.isaac.spec-helper :refer [with-config]]
            [mdm.isaac.goal :as goal]
            [mdm.isaac.thought :as thought]
            [mdm.isaac.think :as sut]
            [mdm.isaac.tool.core :as tool]
            [speclj.core :refer :all]))

(describe "Think"

  (helper/with-schemas [schema.thought/thought schema.setting/config])

  (context "select-goal"

    (it "returns nil when no active goals exist"
      (should-be-nil (sut/select-goal)))

    (it "returns the highest priority active goal"
      (let [embedding (vec (repeat 384 0.1))
            _low (goal/create! "Low priority" embedding {:priority 5})
            high (goal/create! "High priority" embedding {:priority 1})
            _med (goal/create! "Medium priority" embedding {:priority 3})]
        (should= (:id high) (:id (sut/select-goal)))))

    (it "ignores resolved and abandoned goals"
      (let [embedding (vec (repeat 384 0.1))
            active (goal/create! "Active goal" embedding {:priority 5})
            resolved (goal/create! "Resolved" embedding {:priority 1})
            abandoned (goal/create! "Abandoned" embedding {:priority 1})]
        (goal/resolve! resolved)
        (goal/abandon! abandoned)
        (should= (:id active) (:id (sut/select-goal))))))

  (context "retrieve-context"

    (it "returns empty vector when no similar thoughts exist"
      (let [embedding (vec (repeat 384 0.1))]
        (should= [] (sut/retrieve-context embedding 5))))

    (it "returns similar thoughts excluding goals"
      (let [embedding (vec (repeat 384 0.1))
            insight (db/tx {:kind :thought :type "insight" :content "An insight" :embedding embedding})
            _goal (goal/create! "A goal" embedding {:priority 1})
            question (db/tx {:kind :thought :type "question" :content "A question" :embedding embedding})
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
        (should-contain "three fundamental laws" prompt)))

    (it "includes high-seen thoughts as already known"
      (let [embedding (vec (repeat 384 0.1))
            _high-seen (db/tx {:kind :thought :type "insight" :content "Known fact" :embedding embedding :seen-count 5})
            goal {:content "Learn Clojure"}
            prompt (sut/build-prompt goal [])]
        (should-contain "Already Known" prompt)
        (should-contain "Known fact" prompt)))

    (it "does not include low-seen thoughts in already known section"
      (let [embedding (vec (repeat 384 0.1))
            _low-seen (db/tx {:kind :thought :type "insight" :content "New insight" :embedding embedding :seen-count 1})
            goal {:content "Learn Clojure"}
            prompt (sut/build-prompt goal [])]
        (should-not-contain "New insight" prompt))))

  (context "find-high-seen-thoughts"

    (it "returns empty when no thoughts have high seen-count"
      (should= [] (sut/find-high-seen-thoughts)))

    (it "returns thoughts with seen-count above threshold"
      (let [embedding (vec (repeat 384 0.1))
            high (db/tx {:kind :thought :type "insight" :content "Seen many times" :embedding embedding :seen-count 5})
            _low (db/tx {:kind :thought :type "insight" :content "Seen once" :embedding embedding :seen-count 1})]
        (let [results (sut/find-high-seen-thoughts)]
          (should= 1 (count results))
          (should= (:id high) (:id (first results))))))

    (it "respects config threshold"
      (let [embedding (vec (repeat 384 0.1))
            _medium (db/tx {:kind :thought :type "insight" :content "Seen 3 times" :embedding embedding :seen-count 3})]
        ;; Default threshold is 3, so seen-count must be > 3
        (should= [] (sut/find-high-seen-thoughts))
        ;; Set threshold lower
        (db/tx {:kind :config :key :dedupe-high-seen-threshold :value "2"})
        (should= 1 (count (sut/find-high-seen-thoughts)))))

    (it "limits results to 10"
      (let [embedding (vec (repeat 384 0.1))]
        (doseq [i (range 15)]
          (db/tx {:kind :thought :type "insight" :content (str "Thought " i) :embedding embedding :seen-count 5}))
        (should= 10 (count (sut/find-high-seen-thoughts))))))

  (context "parse-response"

    (it "extracts thoughts from LLM response"
      (let [response "INSIGHT: I understand now that Clojure is functional.\nQUESTION: What are macros?"
            thoughts (sut/parse-response response (vec (repeat 384 0.1)))]
        (should= 2 (count thoughts))
        (should= "insight" (:type (first thoughts)))
        (should-contain "functional" (:content (first thoughts)))
        (should= "question" (:type (second thoughts))))))

  (context "think-once!"

    (it "does nothing when no active goals"
      (should-be-nil (sut/think-once! identity)))

    (it "processes a goal and creates new thoughts"
      (let [embedding (vec (repeat 384 0.1))
            mock-llm (fn [_] "INSIGHT: Testing works!")
            _goal (goal/create! "Test goal" embedding {:priority 1})]
        (sut/think-once! mock-llm)
        (let [insights (thought/find-by-type "insight")]
          (should= 1 (count insights))
          (should-contain "Testing works" (:content (first insights)))))))

  (context "start! / stop!"

    (it "stop! sets running? to false"
      (sut/stop!)
      (should= false @sut/running?))

    (it "start! runs iterations until stop! is called"
      (let [embedding (vec (repeat 384 0.1))
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
      (let [embedding (vec (repeat 384 0.1))
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

  (context "service functions"

    (it "start-think starts loop in background thread"
      (let [embedding (vec (repeat 384 0.1))
            call-count (atom 0)
            mock-llm (fn [_]
                       (swap! call-count inc)
                       "INSIGHT: Thinking...")
            _goal (goal/create! "Test goal" embedding {:priority 1})
            app (sut/start-think {} mock-llm {:delay-ms 10})]
        ;; Should return immediately with thread in app
        (should (:think-thread app))
        (should (.isAlive (:think-thread app)))
        ;; Let it run a few iterations
        (Thread/sleep 50)
        (should (>= @call-count 2))
        ;; Clean up
        (sut/stop-think app)))

    (it "stop-think stops the loop gracefully"
      (let [embedding (vec (repeat 384 0.1))
            call-count (atom 0)
            mock-llm (fn [_]
                       (swap! call-count inc)
                       "INSIGHT: Thinking...")
            _goal (goal/create! "Test goal" embedding {:priority 1})
            app (sut/start-think {} mock-llm {:delay-ms 10})]
        (Thread/sleep 30)
        (let [count-before @call-count
              result (sut/stop-think app)]
          ;; Should wait for thread to finish
          (should-not (.isAlive (:think-thread app)))
          ;; Should return app without thread
          (should-not (:think-thread result))
          ;; No more iterations after stop
          (Thread/sleep 30)
          (should= @call-count count-before))))

    (it "stop-think does nothing when no thread running"
      (let [result (sut/stop-think {})]
        (should= {} result)))

    (it "service is defined with start and stop functions"
      (should (map? sut/service))
      (should (:start sut/service))
      (should (:stop sut/service))))

  (context "build-prompt with tools"

    (before (tool/clear!))

    (it "includes tool descriptions when tools are registered"
      (tool/register! {:name :list-goals
                       :description "List all active goals"
                       :params {}
                       :execute identity})
      (let [goal {:content "Test goal"}
            prompt (sut/build-prompt goal [])]
        (should-contain "Available Tools" prompt)
        (should-contain "list-goals" prompt)
        (should-contain "TOOL_CALL" prompt)))

    (it "omits tool section when no tools are registered"
      (let [goal {:content "Test goal"}
            prompt (sut/build-prompt goal [])]
        (should-not-contain "Available Tools" prompt))))

  (context "parse-tool-calls"

    (it "parses a single tool call from response"
      (let [response "Let me check.\nTOOL_CALL: list-goals {}"
            calls (sut/parse-tool-calls response)]
        (should= 1 (count calls))
        (should= :list-goals (:tool (first calls)))))

    (it "parses multiple tool calls from response"
      (let [response (str "TOOL_CALL: list-goals {}\n"
                          "TOOL_CALL: search-thoughts {\"query\": \"clojure\"}")
            calls (sut/parse-tool-calls response)]
        (should= 2 (count calls))
        (should= :list-goals (:tool (first calls)))
        (should= :search-thoughts (:tool (second calls)))))

    (it "returns empty when no tool calls in response"
      (let [response "INSIGHT: This is just a thought."
            calls (sut/parse-tool-calls response)]
        (should= [] calls))))

  (context "execute-tool-calls!"

    (before (tool/clear!))

    (it "executes tool calls and returns results"
      (tool/register! {:name :list-goals
                       :description "List goals"
                       :params {}
                       :execute (fn [_] {:status :ok :goals [{:content "Goal 1"}]})})
      (let [calls [{:tool :list-goals :params {}}]
            results (sut/execute-tool-calls! calls)]
        (should= 1 (count results))
        (should-contain "Goal 1" (first results))))

    (it "returns error message for unknown tools"
      (let [calls [{:tool :nonexistent :params {}}]
            results (sut/execute-tool-calls! calls)]
        (should= 1 (count results))
        (should-contain "Unknown tool" (first results)))))

  (context "think-once! with tools"

    (before (tool/clear!))

    (it "includes tool results when LLM makes tool calls"
      (let [embedding (vec (repeat 384 0.1))
            call-count (atom 0)
            mock-llm (fn [prompt]
                       (let [n (swap! call-count inc)]
                         (if (= 1 n)
                           "TOOL_CALL: list-goals {}"
                           "INSIGHT: Found the goals!")))
            _goal (goal/create! "Test goal" embedding {:priority 1})]
        (tool/register! {:name :list-goals
                         :description "List goals"
                         :params {}
                         :execute (fn [_] {:status :ok :goals []})})
        (let [thoughts (sut/think-once! mock-llm)]
          ;; LLM was called twice: once initially, once with tool results
          (should= 2 @call-count)
          (should= 1 (count thoughts))
          (should= "insight" (:type (first thoughts))))))

    (it "feeds tool results back into prompt"
      (let [embedding (vec (repeat 384 0.1))
            captured-prompts (atom [])
            call-count (atom 0)
            mock-llm (fn [prompt]
                       (swap! captured-prompts conj prompt)
                       (let [n (swap! call-count inc)]
                         (if (= 1 n)
                           "TOOL_CALL: list-goals {}"
                           "INSIGHT: Done!")))
            _goal (goal/create! "Test goal" embedding {:priority 1})]
        (tool/register! {:name :list-goals
                         :description "List goals"
                         :params {}
                         :execute (fn [_] {:status :ok :goals [{:content "My Goal"}]})})
        (sut/think-once! mock-llm)
        ;; The second prompt should contain tool results
        (should-contain "Tool Results" (second @captured-prompts))
        (should-contain "My Goal" (second @captured-prompts))))

    (it "limits tool call iterations to prevent infinite loops"
      (let [embedding (vec (repeat 384 0.1))
            call-count (atom 0)
            mock-llm (fn [_]
                       (swap! call-count inc)
                       "TOOL_CALL: list-goals {}")
            _goal (goal/create! "Test goal" embedding {:priority 1})]
        (tool/register! {:name :list-goals
                         :description "List goals"
                         :params {}
                         :execute (fn [_] {:status :ok :goals []})})
        (sut/think-once! mock-llm)
        ;; Should stop after max iterations (default), not loop forever
        (should (<= @call-count 6))))

    (it "works normally when no tools are registered"
      (let [embedding (vec (repeat 384 0.1))
            mock-llm (fn [_] "INSIGHT: A thought without tools")
            _goal (goal/create! "Test goal" embedding {:priority 1})]
        (let [thoughts (sut/think-once! mock-llm)]
          (should= 1 (count thoughts))
          (should= "insight" (:type (first thoughts)))))))

  )
