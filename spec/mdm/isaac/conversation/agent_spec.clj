(ns mdm.isaac.conversation.agent-spec
  "Specs for the conversation agent - tool execution loop."
  (:require [mdm.isaac.conversation.agent :as sut]
            [mdm.isaac.tool.core :as tool]
            [speclj.core :refer :all]))

(def echo-tool
  {:name :echo
   :description "Echoes input back"
   :params {:text {:type :string :required true}}
   :execute (fn [{:keys [text]}] {:status :ok :result text})})

(def add-tool
  {:name :add
   :description "Adds two numbers"
   :params {:a {:type :number :required true}
            :b {:type :number :required true}}
   :execute (fn [{:keys [a b]}] {:status :ok :result (+ a b)})})

(describe "Conversation Agent"

  (before (tool/clear!))

  (context "tool->llm-tool"

    (it "converts an Isaac tool to OpenAI function-calling format"
      (let [result (sut/tool->llm-tool echo-tool)]
        (should= "function" (:type result))
        (should= "echo" (get-in result [:function :name]))
        (should= "Echoes input back" (get-in result [:function :description]))))

    (it "converts params to JSON Schema properties"
      (let [result (sut/tool->llm-tool add-tool)
            params (get-in result [:function :parameters])]
        (should= "object" (:type params))
        (should-contain :a (:properties params))
        (should-contain :b (:properties params))))

    (it "marks required params in the required array"
      (let [result (sut/tool->llm-tool add-tool)
            required (get-in result [:function :parameters :required])]
        (should-contain "a" required)
        (should-contain "b" required)))

    (it "uses tool name without namespace for function name"
      (let [mcp-tool {:name :mcp/brave_search :description "Search" :params {}}
            result (sut/tool->llm-tool mcp-tool)]
        (should= "brave_search" (get-in result [:function :name])))))

  (context "registry->llm-tools"

    (it "converts all registered tools to LLM format"
      (tool/register! echo-tool)
      (tool/register! add-tool)
      (let [llm-tools (sut/registry->llm-tools)]
        (should= 2 (count llm-tools))
        (should (every? #(= "function" (:type %)) llm-tools)))))

  (context "execute-tool-call"

    (it "executes a tool call and returns the result as a string"
      (tool/register! add-tool)
      (let [tool-call {:name "add" :arguments {:a 2 :b 3}}
            result (sut/execute-tool-call tool-call)]
        (should-contain "result" result)
        (should-contain "5" result)))

    (it "returns error string for unknown tool"
      (let [tool-call {:name "nonexistent" :arguments {}}
            result (sut/execute-tool-call tool-call)]
        (should-contain "Unknown tool" result))))

  (context "tool-calls->messages"

    (it "creates one assistant message and one tool message for a single call"
      (let [tool-calls [{:name "add" :arguments {:a 1 :b 2}}]
            results ["{\"status\":\"ok\",\"result\":3}"]
            messages (sut/tool-calls->messages tool-calls results)]
        (should= 2 (count messages))
        (should= "assistant" (:role (first messages)))
        (should= 1 (count (:tool_calls (first messages))))
        (should= "tool" (:role (second messages)))))

    (it "groups multiple tool calls in one assistant message"
      (let [tool-calls [{:name "add" :arguments {:a 1 :b 2}}
                         {:name "echo" :arguments {:text "hi"}}]
            results ["{\"result\":3}" "{\"result\":\"hi\"}"]
            messages (sut/tool-calls->messages tool-calls results)]
        (should= 3 (count messages))
        (should= "assistant" (:role (first messages)))
        (should= 2 (count (:tool_calls (first messages))))
        (should= "tool" (:role (second messages)))
        (should= "tool" (:role (nth messages 2))))))

  (context "respond!"

    (it "returns content when LLM responds without tool calls"
      (let [llm-fn (fn [_prompt _tools] {:content "Hello!" :tool-calls nil})
            messages [{:role "user" :content "Hi"}]
            result (sut/respond! messages [] llm-fn)]
        (should= "Hello!" (:content result))
        (should= nil (:tool-calls result))))

    (it "returns tool calls when LLM wants to use tools"
      (let [tool-calls [{:name "echo" :arguments {:text "hi"}}]
            llm-fn (fn [_prompt _tools] {:content nil :tool-calls tool-calls})
            messages [{:role "user" :content "Hi"}]
            result (sut/respond! messages [] llm-fn)]
        (should= tool-calls (:tool-calls result)))))

  (context "agent-loop!"

    (it "returns response when LLM responds with no tool calls"
      (let [llm-fn (fn [_prompt _tools] {:content "Just a response" :tool-calls nil})
            messages [{:role "user" :content "Hi"}]
            result (sut/agent-loop! messages [] llm-fn)]
        (should= "Just a response" (:response result))
        (should= 2 (count (:messages result)))))

    (it "executes tools and loops until LLM stops calling tools"
      (tool/register! add-tool)
      (let [call-count (atom 0)
            llm-fn (fn [_prompt _tools]
                     (let [n (swap! call-count inc)]
                       (if (= 1 n)
                         {:content nil :tool-calls [{:name "add" :arguments {:a 2 :b 3}}]}
                         {:content "The answer is 5" :tool-calls nil})))
            messages [{:role "user" :content "What is 2+3?"}]
            result (sut/agent-loop! messages [] llm-fn)]
        (should= "The answer is 5" (:response result))
        ;; messages: user + assistant(tool_call) + tool(result) + assistant(final)
        (should= 4 (count (:messages result)))))

    (it "handles multiple tool calls in one response"
      (tool/register! echo-tool)
      (tool/register! add-tool)
      (let [call-count (atom 0)
            llm-fn (fn [_prompt _tools]
                     (let [n (swap! call-count inc)]
                       (if (= 1 n)
                         {:content nil
                          :tool-calls [{:name "add" :arguments {:a 1 :b 2}}
                                       {:name "echo" :arguments {:text "hello"}}]}
                         {:content "Done: 3 and hello" :tool-calls nil})))
            messages [{:role "user" :content "Do both"}]
            result (sut/agent-loop! messages [] llm-fn)]
        (should= "Done: 3 and hello" (:response result))))

    (it "enforces a maximum iteration limit"
      (tool/register! echo-tool)
      (let [llm-fn (fn [_prompt _tools]
                      {:content nil :tool-calls [{:name "echo" :arguments {:text "loop"}}]})
            messages [{:role "user" :content "Loop forever"}]
            result (sut/agent-loop! messages [] llm-fn {:max-iterations 3})]
        (should-contain "max iterations" (:response result)))))

  (context "chat!"

    (it "sends a user message and returns the response"
      (let [llm-fn (fn [_prompt _tools] {:content "Hello there!" :tool-calls nil})
            history (atom [])
            result (sut/chat! "Hi" {:llm-fn llm-fn :history history})]
        (should= "Hello there!" (:response result))))

    (it "maintains conversation history across calls"
      (let [call-count (atom 0)
            llm-fn (fn [_prompt _tools]
                     (swap! call-count inc)
                     {:content (str "Response " @call-count) :tool-calls nil})
            history (atom [])]
        (sut/chat! "First" {:llm-fn llm-fn :history history})
        (sut/chat! "Second" {:llm-fn llm-fn :history history})
        ;; History should have: user1, assistant1, user2, assistant2
        (should= 4 (count @history))
        (should= "First" (:content (first @history)))
        (should= "Second" (:content (nth @history 2)))))

    (it "includes registered tools in LLM calls"
      (tool/register! echo-tool)
      (let [captured-tools (atom nil)
            llm-fn (fn [_prompt tools]
                     (reset! captured-tools tools)
                     {:content "OK" :tool-calls nil})
            history (atom [])]
        (sut/chat! "Use tools" {:llm-fn llm-fn :history history})
        (should= 1 (count @captured-tools))
        (should= "echo" (get-in (first @captured-tools) [:function :name]))))

    (it "prepends system message to the messages sent to LLM"
      (let [captured-msgs (atom nil)
            llm-fn (fn [msgs _tools]
                     (reset! captured-msgs msgs)
                     {:content "OK" :tool-calls nil})
            history (atom [])]
        (sut/chat! "Hello" {:llm-fn llm-fn :history history})
        (should= "system" (:role (first @captured-msgs)))
        (should-contain "Isaac" (:content (first @captured-msgs)))))

    (it "does not accumulate system messages in history"
      (let [llm-fn (fn [_msgs _tools] {:content "OK" :tool-calls nil})
            history (atom [])]
        (sut/chat! "First" {:llm-fn llm-fn :history history})
        (sut/chat! "Second" {:llm-fn llm-fn :history history})
        ;; History should only have user + assistant messages, no system messages
        (should-not (some #(= "system" (:role %)) @history))
        (should= 4 (count @history)))))

  )
