(ns isaac.acp.server-spec
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [clojure.java.io :as io]
    [isaac.acp.server :as sut]
    [isaac.tool.builtin :as builtin]
    [isaac.llm.grover :as grover]
    [isaac.session.fs :as fs]
    [isaac.session.storage :as storage]
    [isaac.tool.registry :as tool-registry]
    [speclj.core :refer :all]))

(def test-dir "target/test-acp-server")

(defn- clean-dir! [path]
  (let [dir (io/file path)]
    (when (.exists dir)
      (doseq [f (reverse (file-seq dir))]
        (.delete f)))))

(defn- parsed-output [writer]
  (->> (str/split-lines (str writer))
       (remove str/blank?)
       (mapv #(json/parse-string % true))))

(describe "ACP server"

  (before (clean-dir! test-dir))
  (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

  (describe "initialize"

    (it "returns protocol version, agent info, and capabilities"
      (let [response (sut/dispatch-line {:state-dir test-dir}
                                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":1}}")]
        (should= 1 (get-in response [:result :protocolVersion]))
        (should= "isaac" (get-in response [:result :agentInfo :name]))
        (should= true (get-in response [:result :agentCapabilities :loadSession]))
        (should= true (get-in response [:result :agentCapabilities :promptCapabilities :text]))))

    (it "includes model and provider in agentInfo when agents and models are provided"
      (let [agents {"main" {:name "main" :soul "You are Isaac." :model "grover"}}
            models {"grover" {:alias "grover" :model "echo" :provider "grover" :contextWindow 32768}}
            response (sut/dispatch-line {:state-dir test-dir :agents agents :models models}
                                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":1}}")]
        (should= "echo" (get-in response [:result :agentInfo :model]))
        (should= "grover" (get-in response [:result :agentInfo :provider])))))

  (describe "session/new"

    (it "creates an ACP channel session for main agent"
      (let [response   (sut/dispatch-line {:state-dir test-dir}
                                          "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"session/new\",\"params\":{\"cwd\":\"/tmp/project\"}}")
            session-id (or (get-in response [:result :sessionId])
                           (get-in response [:response :result :sessionId]))
            sessions   (storage/list-sessions test-dir "main")]
        (should (string? session-id))
        (should= 1 (count sessions))
        (should= session-id (:id (first sessions)))))

    (it "advertises available slash commands after session creation"
      (let [result       (sut/dispatch-line {:state-dir test-dir}
                                            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"session/new\",\"params\":{\"name\":\"cmd-test\"}}")
            session-id   (get-in result [:response :result :sessionId])
            notification (first (:notifications result))]
        (should= "cmd-test" session-id)
        (should= "session/update" (:method notification))
        (should= "available_commands_update" (get-in notification [:params :update :sessionUpdate]))
        (should= ["status" "model" "crew"]
                 (mapv :name (get-in notification [:params :update :availableCommands]))))))

  (describe "session/prompt"

    (def ^:private test-agents {"main" {:name "main" :soul "You are Isaac." :model "grover"}})
    (def ^:private test-models {"grover" {:alias "grover" :model "echo" :provider "grover" :contextWindow 32768}})
    (def ^:private prompt-opts {:state-dir test-dir :agents test-agents :models test-models})

    (before (grover/reset-queue!))

    (it "returns end_turn stop reason when the prompt completes"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (grover/enqueue! [{:type "text" :content "Four, I think" :model "echo"}])
      (let [response (sut/dispatch-line (assoc prompt-opts :output-writer (java.io.StringWriter.))
                                        (str "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"session/prompt\","
                                             "\"params\":{\"sessionId\":\"agent:main:acp:direct:user1\","
                                             "\"prompt\":[{\"type\":\"text\",\"text\":\"What is 2+2?\"}]}}"))]
        (should= "end_turn" (get-in response [:result :stopReason]))))

    (it "stores user and assistant messages in the transcript"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (grover/enqueue! [{:type "text" :content "Four, I think" :model "echo"}])
      (sut/dispatch-line (assoc prompt-opts :output-writer (java.io.StringWriter.))
                         (str "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"session/prompt\","
                              "\"params\":{\"sessionId\":\"agent:main:acp:direct:user1\","
                              "\"prompt\":[{\"type\":\"text\",\"text\":\"What is 2+2?\"}]}}"))
      (let [transcript (storage/get-transcript test-dir "agent:main:acp:direct:user1")
            messages   (filter #(= "message" (:type %)) transcript)]
        (should= 2 (count messages))
        (should= "user" (get-in (nth messages 0) [:message :role]))
        (should= "assistant" (get-in (nth messages 1) [:message :role]))))

    (it "uses model-override instead of agent's default model"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (let [models-with-alt (assoc test-models "grover2" {:alias "grover2" :model "echo-alt" :provider "grover" :contextWindow 16384})]
        (grover/enqueue! [{:type "text" :content "Hello" :model "echo-alt"}])
        (let [response (sut/dispatch-line {:state-dir      test-dir
                                           :agents         test-agents
                                           :models         models-with-alt
                                           :model-override "grover2"
                                           :output-writer  (java.io.StringWriter.)}
                                          (str "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"session/prompt\","
                                               "\"params\":{\"sessionId\":\"agent:main:acp:direct:user1\","
                                               "\"prompt\":[{\"type\":\"text\",\"text\":\"Hi\"}]}}") )]
          (should= "end_turn" (get-in response [:result :stopReason])))))

    (it "stores model and provider in the assistant message"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (grover/enqueue! [{:type "text" :content "Hello" :model "echo"}])
      (sut/dispatch-line (assoc prompt-opts :output-writer (java.io.StringWriter.))
                         (str "{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"session/prompt\"," 
                              "\"params\":{\"sessionId\":\"agent:main:acp:direct:user1\","
                              "\"prompt\":[{\"type\":\"text\",\"text\":\"Hi\"}]}}"))
      (let [transcript (storage/get-transcript test-dir "agent:main:acp:direct:user1")
            assistant  (->> transcript (filter #(= "message" (:type %))) last)]
        (should= "echo" (get-in assistant [:message :model]))
        (should= "grover" (get-in assistant [:message :provider]))))

    (it "writes one session/update notification per streamed text chunk"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (grover/enqueue! [{:type "text" :content ["Once " "upon " "a " "time..."] :model "echo"}])
      (let [writer        (java.io.StringWriter.)
            result        (sut/dispatch-line (assoc prompt-opts :output-writer writer)
                                             (str "{\"jsonrpc\":\"2.0\",\"id\":20,\"method\":\"session/prompt\","
                                                  "\"params\":{\"sessionId\":\"agent:main:acp:direct:user1\","
                                                  "\"prompt\":[{\"type\":\"text\",\"text\":\"Tell me a story\"}]}}"))
            updates       (parsed-output writer)
            update-texts  (mapv #(get-in % [:params :update :content :text]) updates)
            update-kinds  (mapv #(get-in % [:params :update :sessionUpdate]) updates)
            transcript    (storage/get-transcript test-dir "agent:main:acp:direct:user1")
            assistant-msg (get-in (->> transcript
                                       (filter #(= "message" (:type %)))
                                       last)
                                  [:message :content])]
        (should= "end_turn" (get-in result [:result :stopReason]))
        (should= ["agent_message_chunk" "agent_message_chunk" "agent_message_chunk" "agent_message_chunk"] update-kinds)
        (should= ["Once" "upon" "a" "time..."] update-texts)
        (should= "Once upon a time..." assistant-msg)))

    (it "writes tool_call pending and tool_call_update completed notifications"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (tool-registry/register! {:name "echo-tool" :description "Echoes input" :handler (fn [args] {:result (str "echoed: " (:input args))})})
      (grover/enqueue! [{:tool_call "echo-tool" :arguments {:input "hello"}}
                        {:type "text" :content "Done!" :model "echo"}])
      (let [writer        (java.io.StringWriter.)
            result        (sut/dispatch-line (assoc prompt-opts :output-writer writer)
                                             (str "{\"jsonrpc\":\"2.0\",\"id\":30,\"method\":\"session/prompt\","
                                                  "\"params\":{\"sessionId\":\"agent:main:acp:direct:user1\","
                                                  "\"prompt\":[{\"type\":\"text\",\"text\":\"Use the echo tool\"}]}}"))
            notifications (parsed-output writer)
            kinds         (mapv #(get-in % [:params :update :sessionUpdate]) notifications)]
        (should= "end_turn" (get-in result [:result :stopReason]))
        (should (some #(= "tool_call" %) kinds))
        (should (some #(= "tool_call_update" %) kinds))
        (should (every? #(= "agent:main:acp:direct:user1" (get-in % [:params :sessionId])) notifications))
        (should (some #(= "pending" (get-in % [:params :update :status])) notifications))
        (should (some #(= "completed" (get-in % [:params :update :status])) notifications))))

    (it "writes a session/update text notification for slash commands"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (let [writer        (java.io.StringWriter.)
            result        (sut/dispatch-line (assoc prompt-opts :output-writer writer)
                                             (str "{\"jsonrpc\":\"2.0\",\"id\":40,\"method\":\"session/prompt\","
                                                  "\"params\":{\"sessionId\":\"agent:main:acp:direct:user1\","
                                                  "\"prompt\":[{\"type\":\"text\",\"text\":\"/status\"}]}}"))
            notifications (parsed-output writer)]
        (should= "end_turn" (get-in result [:result :stopReason]))
        (should (some #(= "agent_message_chunk" (get-in % [:params :update :sessionUpdate])) notifications))))

  )

  (describe "session/cancel"

    (before
      (grover/reset-queue!)
      (tool-registry/clear!))

    (it "returns cancelled when interrupt is set before prompt starts"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (grover/enqueue! [{:type "text" :content "Hello" :model "echo"}])
      ;; Send cancel first (sets interrupt for the session)
      (sut/dispatch-line prompt-opts
                         "{\"jsonrpc\":\"2.0\",\"method\":\"session/cancel\",\"params\":{\"sessionId\":\"agent:main:acp:direct:user1\"}}")
      ;; Now send prompt - it should detect the interrupt and return cancelled
       (let [result (-> (sut/dispatch-line prompt-opts
                                           (str "{\"jsonrpc\":\"2.0\",\"id\":30,\"method\":\"session/prompt\","
                                                "\"params\":{\"sessionId\":\"agent:main:acp:direct:user1\","
                                                "\"prompt\":[{\"type\":\"text\",\"text\":\"Long task\"}]}}"))
                        (as-> r (if (future? r) (deref r) r)))]
        (should= "cancelled" (get-in result [:result :stopReason]))))

    (it "returns cancelled when session/cancel interrupts an in-flight LLM request"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (grover/set-delay-ms! 30000)
      (let [prompt (future
                     (sut/dispatch-line (assoc prompt-opts :output-writer (java.io.StringWriter.))
                                        (str "{\"jsonrpc\":\"2.0\",\"id\":31,\"method\":\"session/prompt\","
                                             "\"params\":{\"sessionId\":\"agent:main:acp:direct:user1\","
                                             "\"prompt\":[{\"type\":\"text\",\"text\":\"think hard\"}]}}")))]
        (Thread/sleep 100)
        (sut/dispatch-line prompt-opts
                           "{\"jsonrpc\":\"2.0\",\"method\":\"session/cancel\",\"params\":{\"sessionId\":\"agent:main:acp:direct:user1\"}}")
        (should= "cancelled" (get-in (deref prompt 3000 nil) [:result :stopReason]))))

    (it "returns cancelled when session/cancel interrupts an in-flight exec tool"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (builtin/register-all! tool-registry/register!)
      (grover/enqueue! [{:tool_call "exec" :arguments {:command "sleep 30"}}])
      (let [prompt (future
                     (sut/dispatch-line (assoc prompt-opts :output-writer (java.io.StringWriter.))
                                        (str "{\"jsonrpc\":\"2.0\",\"id\":32,\"method\":\"session/prompt\","
                                             "\"params\":{\"sessionId\":\"agent:main:acp:direct:user1\","
                                             "\"prompt\":[{\"type\":\"text\",\"text\":\"run it\"}]}}")))]
        (Thread/sleep 100)
        (sut/dispatch-line prompt-opts
                           "{\"jsonrpc\":\"2.0\",\"method\":\"session/cancel\",\"params\":{\"sessionId\":\"agent:main:acp:direct:user1\"}}")
        (should= "cancelled" (get-in (deref prompt 1000 nil) [:result :stopReason]))))

  )

)
