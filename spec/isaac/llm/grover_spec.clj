(ns isaac.llm.grover-spec
  (:require
    [isaac.llm.core :as llm]
    [isaac.llm.grover :as grover]
    [speclj.core :refer :all]))

(describe "llm.grover"

  (before
    (grover/reset!)
    (reset! llm/config {:impl :grover}))

  (context "chat"

    (it "returns default response when no script"
      (should= "I am Grover." (llm/chat "hello")))

    (it "returns scripted response"
      (grover/script-response! {:content "scripted answer" :tool-calls nil})
      (should= "scripted answer" (llm/chat "anything")))

    (it "consumes responses in FIFO order"
      (grover/script-responses! [{:content "first" :tool-calls nil}
                                 {:content "second" :tool-calls nil}])
      (should= "first" (llm/chat "a"))
      (should= "second" (llm/chat "b")))

    (it "falls back to default after scripted responses exhausted"
      (grover/script-response! {:content "only one" :tool-calls nil})
      (llm/chat "consume it")
      (should= "I am Grover." (llm/chat "next")))

    (it "records calls"
      (llm/chat "hello world")
      (should= 1 (count (grover/calls)))
      (should= {:type :chat :prompt "hello world"} (grover/last-call))))

  (context "chat-with-tools"

    (it "returns default response when no script"
      (let [result (llm/chat-with-tools [{:role "user" :content "hi"}] [])]
        (should= "I am Grover." (:content result))
        (should= nil (:tool-calls result))))

    (it "returns scripted response with tool calls"
      (grover/script-response! {:content nil
                                :tool-calls [{:name "get_weather"
                                              :arguments {:location "SF"}}]})
      (let [result (llm/chat-with-tools [{:role "user" :content "weather?"}]
                                        [{:type "function"
                                          :function {:name "get_weather"}}])]
        (should= nil (:content result))
        (should= 1 (count (:tool-calls result)))
        (should= "get_weather" (-> result :tool-calls first :name))
        (should= {:location "SF"} (-> result :tool-calls first :arguments))))

    (it "records calls with messages and tools"
      (let [messages [{:role "user" :content "test"}]
            tools [{:type "function" :function {:name "foo"}}]]
        (llm/chat-with-tools messages tools)
        (should= 1 (count (grover/calls)))
        (let [call (grover/last-call)]
          (should= :chat-with-tools (:type call))
          (should= messages (:messages call))
          (should= tools (:tools call)))))

    (it "consumes responses in FIFO order"
      (grover/script-responses! [{:content "first" :tool-calls nil}
                                 {:content "second" :tool-calls nil}])
      (let [r1 (llm/chat-with-tools [{:role "user" :content "a"}] [])
            r2 (llm/chat-with-tools [{:role "user" :content "b"}] [])]
        (should= "first" (:content r1))
        (should= "second" (:content r2)))))

  (context "reset!"

    (it "clears scripted responses"
      (grover/script-response! {:content "will be cleared" :tool-calls nil})
      (grover/reset!)
      (should= "I am Grover." (llm/chat "test")))

    (it "clears call history"
      (llm/chat "recorded")
      (should= 1 (count (grover/calls)))
      (grover/reset!)
      (should= 0 (count (grover/calls))))))
