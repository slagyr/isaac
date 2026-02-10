(ns mdm.isaac.tool.core-spec
  (:require [c3kit.bucket.api :as db]
            [c3kit.bucket.spec-helperc :as helper]
            [mdm.isaac.thought.schema :as schema.thought]
            [mdm.isaac.tool.core :as sut]
            [speclj.core :refer :all]))

(describe "Tool System"

  (helper/with-schemas [schema.thought/thought])

  (context "tool registration"

    (it "registers a tool"
      (let [tool {:name :test-tool
                  :description "A test tool"
                  :execute (fn [_] {:result "ok"})}]
        (sut/register! tool)
        (should= tool (sut/get-tool :test-tool))))

    (it "lists all registered tools"
      (sut/clear!)
      (let [tool1 {:name :tool-1 :description "Tool 1" :execute identity}
            tool2 {:name :tool-2 :description "Tool 2" :execute identity}]
        (sut/register! tool1)
        (sut/register! tool2)
        (should= 2 (count (sut/all-tools)))))

    (it "returns nil for unregistered tool"
      (should-be-nil (sut/get-tool :nonexistent-tool))))

  (context "tool execution"

    (it "executes a tool and returns result"
      (let [tool {:name :echo
                  :description "Echo params"
                  :execute (fn [params] {:echoed params})}]
        (sut/register! tool)
        (let [result (sut/execute! :echo {:message "hello"})]
          (should= {:echoed {:message "hello"}} result))))

    (it "returns error for unknown tool"
      (let [result (sut/execute! :unknown-tool {})]
        (should= :error (:status result))
        (should-contain "Unknown tool" (:message result)))))

  (context "tool prompt formatting"

    (it "formats tools for LLM prompt"
      (sut/clear!)
      (sut/register! {:name :create-goal
                      :description "Create a new goal"
                      :params {:content {:type :string :required true}
                               :priority {:type :long :required false}}
                      :execute identity})
      (let [prompt (sut/tools-prompt)]
        (should-contain "create-goal" prompt)
        (should-contain "Create a new goal" prompt)
        (should-contain "content" prompt))))

  (context "tool call parsing"

    (it "parses tool call from LLM response"
      (let [response "I'll create a goal for you.\nTOOL_CALL: create-goal {\"content\": \"Learn Clojure\"}"
            parsed (sut/parse-tool-call response)]
        (should= :create-goal (:tool parsed))
        (should= {:content "Learn Clojure"} (:params parsed))))

    (it "returns nil when no tool call in response"
      (let [response "Just a regular response without any tool calls."
            parsed (sut/parse-tool-call response)]
        (should-be-nil parsed)))

    (it "parses tool call with multiple params"
      (let [response "TOOL_CALL: create-goal {\"content\": \"Learn macros\", \"priority\": 2}"
            parsed (sut/parse-tool-call response)]
        (should= :create-goal (:tool parsed))
        (should= {:content "Learn macros" :priority 2} (:params parsed)))))

  )
