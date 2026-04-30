(ns isaac.llm.grover-spec
  (:require
    [isaac.session.bridge :as bridge]
    [isaac.llm.grover :as sut]
    [speclj.core :refer :all]))

(describe "Grover"

  (before (sut/reset-queue!))

  ;; region ----- Echo Mode -----

  (describe "echo mode"

    (it "echoes the last user message"
      (let [resp (sut/chat {:model    "echo"
                            :messages [{:role "user" :content "Hello Grover"}]})]
        (should= "Hello Grover" (get-in resp [:message :content]))))

    (it "echoes the last user message when multiple"
      (let [resp (sut/chat {:model    "echo"
                            :messages [{:role "user" :content "First"}
                                       {:role "assistant" :content "Reply"}
                                       {:role "user" :content "Second"}]})]
        (should= "Second" (get-in resp [:message :content]))))

    (it "returns '...' when no user messages"
      (let [resp (sut/chat {:model    "echo"
                            :messages [{:role "system" :content "You are helpful"}]})]
        (should= "..." (get-in resp [:message :content]))))

    (it "returns the model from the request"
      (let [resp (sut/chat {:model    "test-model"
                            :messages [{:role "user" :content "Hi"}]})]
        (should= "test-model" (:model resp))))

    (it "returns a context length error when the prompt exceeds the configured context window"
      (let [resp (sut/chat {:model    "echo"
                            :messages [{:role "user" :content (apply str (repeat 240 "x"))}]}
                           {:provider-config {:context-window 20
                                              :enforce-context-window true}})]
        (should= :llm-error (:error resp))
        (should= "context length exceeded" (:message resp))))

    (it "returns cancelled when a delayed response is interrupted"
      (let [turn   (bridge/begin-turn! "grover-cancel")
             _      (sut/enable-delay!)
             result (future (sut/chat {:model    "echo"
                                      :messages [{:role "user" :content "Hi"}]}
                                     {:provider-config {:session-key "grover-cancel"}}))]
        (sut/await-delay-start)
        (bridge/cancel! "grover-cancel")
        (sut/release-delay!)
        (should= :cancelled (:error @result))
        (bridge/end-turn! "grover-cancel" turn)))

    (it "includes token counts"
      (let [resp (sut/chat {:model    "echo"
                            :messages [{:role "user" :content "Hi"}]})]
        (should= 25 (:prompt_eval_count resp))
        (should= 12 (:eval_count resp))))

    (it "marks response as done"
      (let [resp (sut/chat {:model    "echo"
                            :messages [{:role "user" :content "Hi"}]})]
        (should (:done resp))
        (should= "stop" (:done_reason resp)))))

  ;; endregion ^^^^^ Echo Mode ^^^^^

  ;; region ----- Scripted Mode -----

  (describe "scripted mode"

    (it "returns queued content response"
      (sut/enqueue! [{:content "Scripted answer"}])
      (let [resp (sut/chat {:model    "echo"
                            :messages [{:role "user" :content "Ignored"}]})]
        (should= "Scripted answer" (get-in resp [:message :content]))))

    (it "consumes queue in order"
      (sut/enqueue! [{:content "First"} {:content "Second"}])
      (should= "First" (get-in (sut/chat {:model "echo" :messages []}) [:message :content]))
      (should= "Second" (get-in (sut/chat {:model "echo" :messages []}) [:message :content])))

    (it "falls back to echo when queue is empty"
      (sut/enqueue! [{:content "Only one"}])
      (sut/chat {:model "echo" :messages []})
      (let [resp (sut/chat {:model    "echo"
                            :messages [{:role "user" :content "Echo me"}]})]
        (should= "Echo me" (get-in resp [:message :content]))))

    (it "throws exception for exception type"
      (sut/enqueue! [{:type "exception" :content "something broke"}])
      (should-throw Exception "something broke"
        (sut/chat {:model "echo" :messages [{:role "user" :content "boom"}]})))

    (it "returns scripted tool call"
      (sut/enqueue! [{:tool_call "read_file" :arguments {:path "README"}}])
      (let [resp (sut/chat {:model "echo" :messages [{:role "user" :content "Read it"}]})]
        (should= "read_file" (get-in resp [:message :tool_calls 0 :function :name]))
        (should= {:path "README"} (get-in resp [:message :tool_calls 0 :function :arguments]))
        (should= "" (get-in resp [:message :content])))))

  ;; endregion ^^^^^ Scripted Mode ^^^^^

  ;; region ----- Streaming -----

  (describe "chat-stream"

    (it "calls on-chunk for each word"
      (let [chunks (atom [])
            resp   (sut/chat-stream
                     {:model "echo" :messages [{:role "user" :content "Hello world"}]}
                     (fn [c] (swap! chunks conj c)))]
        (should (> (count @chunks) 1))
        (should= "Hello world" (get-in resp [:message :content]))))

    (it "final chunk has done true"
      (let [chunks (atom [])
            _      (sut/chat-stream
                     {:model "echo" :messages [{:role "user" :content "Hi"}]}
                     (fn [c] (swap! chunks conj c)))]
        (should (:done (last @chunks)))))

    (it "streams scripted chunk vectors and returns concatenated final content"
      (sut/enqueue! [{:content ["Once " "upon " "a " "time..."]}])
      (let [chunks      (atom [])
            resp        (sut/chat-stream
                         {:model "echo" :messages [{:role "user" :content "Ignored"}]}
                         (fn [c] (swap! chunks conj c)))
            chunk-texts (mapv #(get-in % [:message :content]) (butlast @chunks))]
        (should= ["Once " "upon " "a " "time..."] chunk-texts)
        (should= "Once upon a time..." (get-in resp [:message :content]))
        (should (:done (last @chunks)))))

    (it "strips tool calls from streamed final chunk when disabled"
      (sut/enqueue! [{:tool_call "exec" :arguments {:command "echo hi"}}])
      (let [chunks (atom [])
            resp   (sut/chat-stream
                    {:model "echo" :messages [{:role "user" :content "Run echo hi"}]}
                    (fn [c] (swap! chunks conj c))
                    {:provider-config {:streamSupportsToolCalls false}})]
        (should-be-nil (get-in resp [:message :tool_calls]))
        (should-be-nil (get-in (last @chunks) [:message :tool_calls]))))

    (it "accepts string false for streamSupportsToolCalls"
      (sut/enqueue! [{:tool_call "exec" :arguments {:command "echo hi"}}])
      (let [resp (sut/chat-stream
                   {:model "echo" :messages [{:role "user" :content "Run echo hi"}]}
                   (fn [_] nil)
                   {:provider-config {:streamSupportsToolCalls "false"}})]
        (should-be-nil (get-in resp [:message :tool_calls]))))

  ;; endregion ^^^^^ Streaming ^^^^^

  ;; region ----- Tool Call Loop -----

  (describe "followup-messages"

    (it "appends assistant tool_calls and role=tool replies"
      (let [response     {:message {:content    ""
                                    :tool_calls [{:function {:name "read" :arguments {}}}]}}
            request      {:messages [{:role "user" :content "Go"}]}
            tool-calls   [{:id "tc1" :name "read" :arguments {}}]
            tool-results ["file contents"]
            messages     (sut/followup-messages request response tool-calls tool-results)]
        (should= 3 (count messages))
        (should= "assistant" (:role (nth messages 1)))
        (should= [{:function {:name "read" :arguments {}}}]
                 (:tool_calls (nth messages 1)))
        (should= {:role "tool" :content "file contents"} (nth messages 2)))))

  ;; endregion ^^^^^ Tool Call Loop ^^^^^

  ))
