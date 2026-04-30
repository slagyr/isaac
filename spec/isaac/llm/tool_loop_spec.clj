(ns isaac.llm.tool-loop-spec
  (:require
    [isaac.llm.tool-loop :as sut]
    [speclj.core :refer :all]))

(defn- queue-chat
  "Build a chat-fn that returns successive responses from the given queue.
   After the queue is drained, throws to fail loud."
  [responses]
  (let [remaining (atom responses)]
    (fn [_request]
      (let [resp (first @remaining)]
        (when-not resp
          (throw (ex-info "queue exhausted" {})))
        (swap! remaining rest)
        resp))))

(defn- recording-followup
  "Followup-fn that records each call into the given atom and returns a
   new :messages vector by appending a marker."
  [calls]
  (fn [request response tool-calls tool-results]
    (swap! calls conj {:request       request
                       :response      response
                       :tool-calls    tool-calls
                       :tool-results  tool-results})
    (conj (vec (:messages request))
          {:role "assistant" :marker (count @calls)})))

(describe "tool-loop/run"

  (it "returns immediately when the first response has no tool-calls"
    (let [chat-fn     (queue-chat [{:message {:role "assistant" :content "done"}
                                    :usage   {:input-tokens 5 :output-tokens 2}}])
          followup-fn (recording-followup (atom []))
          tool-fn     (fn [_ _] "should-not-run")
          result      (sut/run chat-fn followup-fn {:messages []} tool-fn)]
      (should= [] (:tool-calls result))
      (should= 5 (:input-tokens (:token-counts result)))
      (should= 2 (:output-tokens (:token-counts result)))
      (should= false (:loop-request? result))))

  (it "executes tools and recurs when the response has tool-calls"
    (let [chat-fn     (queue-chat
                        [{:tool-calls [{:id "tc1" :name "read" :arguments {:path "x"}}]
                          :usage      {:input-tokens 10 :output-tokens 5}}
                         {:message {:role "assistant" :content "done"}
                          :usage   {:input-tokens 7 :output-tokens 3}}])
          tool-runs   (atom [])
          tool-fn     (fn [name args]
                        (swap! tool-runs conj {:name name :args args})
                        (str "ran " name))
          followup-fn (recording-followup (atom []))
          result      (sut/run chat-fn followup-fn {:messages []} tool-fn)]
      (should= 1 (count (:tool-calls result)))
      (should= "read" (:name (first (:tool-calls result))))
      (should= [{:name "read" :args {:path "x"}}] @tool-runs)
      (should= 17 (:input-tokens (:token-counts result)))
      (should= 8 (:output-tokens (:token-counts result)))
      (should= false (:loop-request? result))))

  (it "passes the followup-fn the prior request, response, tool-calls, and results"
    (let [calls       (atom [])
          chat-fn     (queue-chat
                        [{:tool-calls [{:id "tc1" :name "read" :arguments {:p "x"}}]
                          :usage      {:input-tokens 1 :output-tokens 1}}
                         {:message {:content "done"} :usage {}}])
          tool-fn     (fn [_ _] "result")
          followup-fn (recording-followup calls)]
      (sut/run chat-fn followup-fn {:messages [{:role "user" :content "go"}]} tool-fn)
      (should= 1 (count @calls))
      (let [call (first @calls)]
        (should= [{:role "user" :content "go"}] (:messages (:request call)))
        (should= [{:id "tc1" :name "read" :arguments {:p "x"}}] (:tool-calls call))
        (should= ["result"] (:tool-results call)))))

  (it "stops at max-loops with loop-request? true and unrun tail tools"
    (let [chat-fn     (queue-chat
                        [{:tool-calls [{:id "tc1" :name "a" :arguments {}}]
                          :usage      {:input-tokens 1 :output-tokens 1}}
                         ;; Second response also has tool-calls — but max-loops=1 stops us
                         {:tool-calls [{:id "tc2" :name "b" :arguments {}}]
                          :usage      {:input-tokens 1 :output-tokens 1}}])
          tool-runs   (atom [])
          tool-fn     (fn [name _]
                        (swap! tool-runs conj name)
                        "ok")
          followup-fn (recording-followup (atom []))
          result      (sut/run chat-fn followup-fn {:messages []} tool-fn {:max-loops 1})]
      ;; Only the first iteration's tool ran; the second response's tools were not executed
      (should= ["a"] @tool-runs)
      (should= 1 (count (:tool-calls result)))
      (should= true (:loop-request? result))))

  (it "stops at max-loops zero before invoking any tools"
    (let [chat-fn     (queue-chat
                        [{:tool-calls [{:id "tc1" :name "a" :arguments {}}]
                          :usage      {:input-tokens 5 :output-tokens 2}}])
          tool-runs   (atom [])
          tool-fn     (fn [_ _]
                        (swap! tool-runs conj :ran)
                        "ok")
          followup-fn (recording-followup (atom []))
          result      (sut/run chat-fn followup-fn {:messages []} tool-fn {:max-loops 0})]
      (should= [] @tool-runs)
      (should= [] (:tool-calls result))
      (should= true (:loop-request? result))))

  (it "returns the error response immediately"
    (let [chat-fn     (queue-chat [{:error :connection-refused}])
          tool-fn     (fn [_ _] "should-not-run")
          followup-fn (recording-followup (atom []))
          result      (sut/run chat-fn followup-fn {:messages []} tool-fn)]
      (should= :connection-refused (:error result))))

  (it "extracts tool-calls from [:message :tool_calls] when not at top-level"
    ;; Some providers (ollama, grover) put tool_calls inside :message rather than
    ;; at the top level. The loop should find them either way.
    (let [chat-fn     (queue-chat
                        [{:message {:role "assistant" :content ""
                                    :tool_calls [{:function {:name "read" :arguments {:p "x"}}}]}}
                         {:message {:role "assistant" :content "done"}}])
          tool-runs   (atom [])
          tool-fn     (fn [name args]
                        (swap! tool-runs conj {:name name :args args})
                        "ok")
          followup-fn (recording-followup (atom []))
          result      (sut/run chat-fn followup-fn {:messages []} tool-fn)]
      (should= 1 (count @tool-runs))
      (should= "read" (:name (first @tool-runs)))
      (should= {:p "x"} (:args (first @tool-runs)))
      (should= 1 (count (:tool-calls result)))))

  (it "accumulates token counts across iterations"
    (let [chat-fn     (queue-chat
                        [{:tool-calls [{:id "tc1" :name "a" :arguments {}}]
                          :usage      {:input-tokens 10 :output-tokens 5}}
                         {:tool-calls [{:id "tc2" :name "b" :arguments {}}]
                          :usage      {:input-tokens 7 :output-tokens 3}}
                         {:message {:content "done"}
                          :usage   {:input-tokens 4 :output-tokens 1}}])
          tool-fn     (fn [_ _] "ok")
          followup-fn (recording-followup (atom []))
          result      (sut/run chat-fn followup-fn {:messages []} tool-fn)]
      (should= 21 (:input-tokens (:token-counts result)))
      (should= 9 (:output-tokens (:token-counts result))))))
