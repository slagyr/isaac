(ns isaac.acp.server-spec
  (:require
    [clojure.java.io :as io]
    [isaac.acp.server :as sut]
    [isaac.llm.grover :as grover]
    [isaac.session.storage :as storage]
    [speclj.core :refer :all]))

(def test-dir "target/test-acp-server")

(defn- clean-dir! [path]
  (let [dir (io/file path)]
    (when (.exists dir)
      (doseq [f (reverse (file-seq dir))]
        (.delete f)))))

(describe "ACP server"

  (before (clean-dir! test-dir))

  (describe "initialize"

    (it "returns protocol version, agent info, and capabilities"
      (let [response (sut/dispatch-line {:state-dir test-dir}
                                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":1}}")]
        (should= 1 (get-in response [:result :protocolVersion]))
        (should= "isaac" (get-in response [:result :agentInfo :name]))
        (should= true (get-in response [:result :agentCapabilities :loadSession]))
        (should= true (get-in response [:result :agentCapabilities :promptCapabilities :text])))))

  (describe "session/new"

    (it "creates an ACP channel session for main agent"
      (let [response   (sut/dispatch-line {:state-dir test-dir}
                                          "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"session/new\",\"params\":{\"cwd\":\"/tmp/project\"}}")
            session-id (get-in response [:result :sessionId])
            sessions   (storage/list-sessions test-dir "main")]
        (should (re-matches #"agent:main:acp:direct:.+" session-id))
        (should= 1 (count sessions))
        (should= session-id (:key (first sessions))))))

  (describe "session/prompt"

    (def ^:private test-agents {"main" {:name "main" :soul "You are Isaac." :model "grover"}})
    (def ^:private test-models {"grover" {:alias "grover" :model "echo" :provider "grover" :contextWindow 32768}})
    (def ^:private prompt-opts {:state-dir test-dir :agents test-agents :models test-models})

    (before (grover/reset-queue!))

    (it "returns end_turn stop reason when the prompt completes"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (grover/enqueue! [{:type "text" :content "Four, I think" :model "echo"}])
      (let [response (sut/dispatch-line prompt-opts
                                        (str "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"session/prompt\","
                                             "\"params\":{\"sessionId\":\"agent:main:acp:direct:user1\","
                                             "\"prompt\":[{\"type\":\"text\",\"text\":\"What is 2+2?\"}]}}"))]
        (should= "end_turn" (get-in response [:result :stopReason]))))

    (it "stores user and assistant messages in the transcript"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (grover/enqueue! [{:type "text" :content "Four, I think" :model "echo"}])
      (sut/dispatch-line prompt-opts
                         (str "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"session/prompt\","
                              "\"params\":{\"sessionId\":\"agent:main:acp:direct:user1\","
                              "\"prompt\":[{\"type\":\"text\",\"text\":\"What is 2+2?\"}]}}"))
      (let [transcript (storage/get-transcript test-dir "agent:main:acp:direct:user1")
            messages   (filter #(= "message" (:type %)) transcript)]
        (should= 2 (count messages))
        (should= "user" (get-in (nth messages 0) [:message :role]))
        (should= "assistant" (get-in (nth messages 1) [:message :role]))))

    (it "stores model and provider in the assistant message"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (grover/enqueue! [{:type "text" :content "Hello" :model "echo"}])
      (sut/dispatch-line prompt-opts
                         (str "{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"session/prompt\","
                              "\"params\":{\"sessionId\":\"agent:main:acp:direct:user1\","
                              "\"prompt\":[{\"type\":\"text\",\"text\":\"Hi\"}]}}"))
      (let [transcript (storage/get-transcript test-dir "agent:main:acp:direct:user1")
            assistant  (->> transcript (filter #(= "message" (:type %))) last)]
        (should= "echo" (get-in assistant [:message :model]))
        (should= "grover" (get-in assistant [:message :provider])))))

  )
