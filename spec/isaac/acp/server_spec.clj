(ns isaac.acp.server-spec
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [isaac.acp.jsonrpc :as jrpc]
    [isaac.acp.server :as sut]
    [isaac.drive.turn :as single-turn]
    [isaac.tool.builtin :as builtin]
    [isaac.llm.grover :as grover]
    [isaac.session.bridge :as bridge]
    [isaac.fs :as fs]
    [isaac.session.storage :as storage]
    [isaac.tool.registry :as tool-registry]
    [speclj.core :refer :all])
  (:import (java.io StringWriter)))

(def test-dir "/test/acp-server")

(defn- clean-dir! [path]
  (doseq [file (fs/children path)]
    (fs/delete (str path "/" file))))

(defn- parsed-output [writer]
  (->> (str/split-lines (str writer))
       (remove str/blank?)
       (mapv #(json/parse-string % true))))

(def ^:private test-agents {"main" {:name "main" :soul "You are Isaac." :model "grover"}})
(def ^:private test-models {"grover" {:alias "grover" :model "echo" :provider "grover" :context-window 32768}})
(def ^:private prompt-opts {:state-dir test-dir :crew-members test-agents :models test-models})

(describe "ACP server"

  (before (clean-dir! test-dir))
  (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

  (describe "initialize"

    (it "returns protocol version, agent info, and capabilities"
      (let [response (sut/dispatch-line {:state-dir test-dir}
                                        (jrpc/request-line 1 "initialize" {:protocolVersion 1}))]
        (should= 1 (get-in response [:result :protocolVersion]))
        (should= "isaac" (get-in response [:result :agentInfo :name]))
        (should= true (get-in response [:result :agentCapabilities :loadSession]))
        (should= true (get-in response [:result :agentCapabilities :promptCapabilities :text]))))

    (it "includes model and provider in agentInfo when agents and models are provided"
      (let [agents {"main" {:name "main" :soul "You are Isaac." :model "grover"}}
            models {"grover" {:alias "grover" :model "echo" :provider "grover" :context-window 32768}}
            response (sut/dispatch-line {:state-dir test-dir :crew-members agents :models models}
                                        (jrpc/request-line 1 "initialize" {:protocolVersion 1}))]
        (should= "echo" (get-in response [:result :agentInfo :model]))
        (should= "grover" (get-in response [:result :agentInfo :provider])))))

  (describe "session/new"

    (it "creates an ACP channel session for main agent"
      (let [response   (sut/dispatch-line {:state-dir test-dir}
                                          (jrpc/request-line 2 "session/new" {:cwd "/tmp/project"}))
            session-id (or (get-in response [:result :sessionId])
                           (get-in response [:response :result :sessionId]))
            sessions   (storage/list-sessions test-dir "main")]
        (should (string? session-id))
        (should= 1 (count sessions))
        (should= session-id (:id (first sessions)))))

    (it "advertises available slash commands after session creation"
      (let [result       (sut/dispatch-line {:state-dir test-dir}
                                            (jrpc/request-line 2 "session/new" {:name "cmd-test"}))
            session-id   (get-in result [:response :result :sessionId])
            notification (first (:notifications result))]
        (should= "cmd-test" session-id)
        (should= "session/update" (:method notification))
        (should= "available_commands_update" (get-in notification [:params :update :sessionUpdate]))
        (should= ["status" "model" "crew" "cwd"]
                 (mapv :name (get-in notification [:params :update :availableCommands])))))

    (it "stores acp origin on sessions created through session/new"
      (let [response   (sut/dispatch-line {:state-dir test-dir}
                                          (jrpc/request-line 2 "session/new" {:name "primary"}))
            session-id (or (get-in response [:result :sessionId])
                           (get-in response [:response :result :sessionId]))
            session    (storage/get-session test-dir session-id)]
        (should= {:kind :acp} (:origin session))))

    )

  (describe "session/prompt"

    (before
      (grover/reset-queue!)
      (tool-registry/clear!))

    (it "returns end_turn stop reason when the prompt completes"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (grover/enqueue! [{:type "text" :content "Four, I think" :model "echo"}])
      (let [response (sut/dispatch-line (assoc prompt-opts :output-writer (StringWriter.))
                                        (jrpc/request-line 10 "session/prompt"
                                                           {:sessionId "agent:main:acp:direct:user1"
                                                            :prompt [{:type "text" :text "What is 2+2?"}]}))]
        (should= "end_turn" (get-in response [:result :stopReason]))))

    (it "stores user and assistant messages in the transcript"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (grover/enqueue! [{:type "text" :content "Four, I think" :model "echo"}])
      (sut/dispatch-line (assoc prompt-opts :output-writer (StringWriter.))
                         (jrpc/request-line 10 "session/prompt"
                                            {:sessionId "agent:main:acp:direct:user1"
                                             :prompt [{:type "text" :text "What is 2+2?"}]}))
      (let [transcript (storage/get-transcript test-dir "agent:main:acp:direct:user1")
            messages   (filter #(= "message" (:type %)) transcript)]
        (should= 2 (count messages))
        (should= "user" (get-in (nth messages 0) [:message :role]))
        (should= "assistant" (get-in (nth messages 1) [:message :role]))))

    (it "uses model-override instead of agent's default model"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (let [models-with-alt (assoc test-models "grover2" {:alias "grover2" :model "echo-alt" :provider "grover" :context-window 16384})]
        (grover/enqueue! [{:type "text" :content "Hello" :model "echo-alt"}])
        (let [response (sut/dispatch-line {:state-dir      test-dir
                                           :crew-members         test-agents
                                           :models         models-with-alt
                                           :model-override "grover2"
                                           :output-writer  (StringWriter.)}
                                          (jrpc/request-line 10 "session/prompt"
                                                             {:sessionId "agent:main:acp:direct:user1"
                                                              :prompt [{:type "text" :text "Hi"}]}))]
          (should= "end_turn" (get-in response [:result :stopReason])))))

    (it "stores model and provider in the assistant message"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (grover/enqueue! [{:type "text" :content "Hello" :model "echo"}])
      (sut/dispatch-line (assoc prompt-opts :output-writer (StringWriter.))
                         (jrpc/request-line 11 "session/prompt"
                                            {:sessionId "agent:main:acp:direct:user1"
                                             :prompt [{:type "text" :text "Hi"}]}))
      (let [transcript (storage/get-transcript test-dir "agent:main:acp:direct:user1")
            assistant  (->> transcript (filter #(= "message" (:type %))) last)]
        (should= "echo" (get-in assistant [:message :model]))
        (should= "grover" (get-in assistant [:message :provider]))))

    (it "writes one session/update notification per streamed text chunk"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (grover/enqueue! [{:type "text" :content ["Once " "upon " "a " "time..."] :model "echo"}])
      (let [writer        (StringWriter.)
            result        (sut/dispatch-line (assoc prompt-opts :output-writer writer)
                                             (jrpc/request-line 20 "session/prompt"
                                                                {:sessionId "agent:main:acp:direct:user1"
                                                                 :prompt [{:type "text" :text "Tell me a story"}]}))
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
      (let [tool-agents   {"main" {:name "main" :soul "You are Isaac." :model "grover" :tools {:allow ["echo-tool"]}}}
            writer        (StringWriter.)
            result        (sut/dispatch-line (assoc prompt-opts :crew-members tool-agents :output-writer writer)
                                             (jrpc/request-line 30 "session/prompt"
                                                                {:sessionId "agent:main:acp:direct:user1"
                                                                 :prompt [{:type "text" :text "Use the echo tool"}]}))
            notifications (parsed-output writer)
            kinds         (mapv #(get-in % [:params :update :sessionUpdate]) notifications)]
        (should= "end_turn" (get-in result [:result :stopReason]))
        (should (some #(= "tool_call" %) kinds))
         (should (some #(= "tool_call_update" %) kinds))
         (should (every? #(= "agent:main:acp:direct:user1" (get-in % [:params :sessionId])) notifications))
         (should (some #(= "pending" (get-in % [:params :update :status])) notifications))
         (should (some #(= "completed" (get-in % [:params :update :status])) notifications))))

    (it "uses configured crew members when prompt handling is driven by cfg"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (let [cfg           {:defaults  {:crew "main" :model "grover"}
                           :crew      {"main" {:soul "You are Isaac."
                                                :tools {:allow [:read :write :exec]}}}
                           :models    {"grover" {:model "echo" :provider "grover" :context-window 32768}}
                           :providers {"grover" {}}}
            captured-opts (atom nil)
            response      (with-redefs [single-turn/run-turn!
                                        (fn [_state-dir _session-id _text opts]
                                          (reset! captured-opts opts)
                                          {})]
                            (sut/dispatch-line {:state-dir     test-dir
                                                :cfg           cfg
                                                :output-writer (StringWriter.)}
                                               (jrpc/request-line 30 "session/prompt"
                                                                  {:sessionId "agent:main:acp:direct:user1"
                                                                   :prompt [{:type "text" :text "Use the configured tools"}]})))]
        (should= "end_turn" (get-in response [:result :stopReason]))
        (should= [:read :write :exec]
                 (get-in @captured-opts [:crew-members "main" :tools :allow]))))

    (it "returns content through ACP when codex responses API emits tool call SSE events"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (tool-registry/register! {:name "read"
                                :description "Read file contents or list a directory"
                                :handler #'builtin/read-tool})
      (let [codex-agents {"main" {:name "main" :soul "Lives in a trash can." :model "snuffy" :tools {:allow ["read"]}}}
            codex-models {"snuffy" {:alias "snuffy" :model "snuffy-codex" :provider "grover:openai-chatgpt" :context-window 128000}}
            lid-file     (str test-dir "/trash-lid.txt")]
        (fs/mkdirs lid-file)
        (fs/spit lid-file "Old newspaper and a banana peel.")
        (grover/enqueue! [{:model "snuffy-codex" :tool_call "read" :arguments {:filePath lid-file}}
                          {:model "snuffy-codex" :type "text" :content "Old newspaper and a banana peel."}])
        (let [writer        (StringWriter.)
              response      (sut/dispatch-line {:state-dir     test-dir
                                                :crew-members        codex-agents
                                                :models        codex-models
                                                :output-writer writer}
                                               (jrpc/request-line 31 "session/prompt"
                                                                  {:sessionId "agent:main:acp:direct:user1"
                                                                   :prompt [{:type "text" :text "what's under the lid?"}]}))
              notifications (parsed-output writer)
              transcript    (storage/get-transcript test-dir "agent:main:acp:direct:user1")
              assistant-msg (get-in (->> transcript
                                         (filter #(= "message" (:type %)))
                                         last)
                                    [:message :content])]
          (should= "end_turn" (get-in response [:result :stopReason]))
          (should (some #(= "agent_message_chunk" (get-in % [:params :update :sessionUpdate]))
                        notifications))
          (should= "Old newspaper and a banana peel." assistant-msg))))

    (it "sends provider error text as an agent message chunk and returns end_turn"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (grover/enqueue! [{:type "error" :content "You exceeded your current quota"}])
      (let [writer         (StringWriter.)
            response       (sut/dispatch-line (assoc prompt-opts :output-writer writer)
                                              (jrpc/request-line 10 "session/prompt"
                                                                 {:sessionId "agent:main:acp:direct:user1"
                                                                  :prompt [{:type "text" :text "Hello"}]}))
            notifications  (parsed-output writer)]
        (should= "end_turn" (get-in response [:result :stopReason]))
        (should-not (get-in response [:result :error]))
        (should (some #(= "agent_message_chunk" (get-in % [:params :update :sessionUpdate])) notifications))
        (should (some #(= "You exceeded your current quota" (get-in % [:params :update :content :text])) notifications))))

    (it "sends connection refused text as an agent message chunk and returns end_turn"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (let [writer   (StringWriter.)
            response (sut/dispatch-line {:state-dir        test-dir
                                         :crew-members           {"main" {:name "main" :soul "You are Isaac." :model "local"}}
                                         :models           {"local" {:alias "local" :model "llama3.2:latest" :provider "ollama" :context-window 32000}}
                                         :provider-configs {"ollama" {:name "ollama" :base-url "http://localhost:99999"}}
                                         :output-writer    writer}
                                        (jrpc/request-line 11 "session/prompt"
                                                           {:sessionId "agent:main:acp:direct:user1"
                                                            :prompt [{:type "text" :text "Hello"}]}))
            notifications (parsed-output writer)]
        (should= "end_turn" (get-in response [:result :stopReason]))
        (should-not (get-in response [:result :error]))
        (should (some #(= "agent_message_chunk" (get-in % [:params :update :sessionUpdate])) notifications))
        (should (some #(str/includes? (get-in % [:params :update :content :text]) "Could not connect") notifications))))

    (it "emits unknown crew guidance exactly once with a visible placeholder"
      (storage/create-session! test-dir "agent:main:acp:direct:user1" {:crew "marvin"})
      (let [writer        (StringWriter.)
            response      (sut/dispatch-line {:state-dir     test-dir
                                             :crew-members  {"main" {:name "main" :soul "You are Isaac." :model "grover"}}
                                             :models        {"grover" {:alias "grover" :model "echo" :provider "grover" :context-window 32768}}
                                             :output-writer writer}
                                            (jrpc/request-line 12 "session/prompt"
                                                               {:sessionId "agent:main:acp:direct:user1"
                                                                :prompt [{:type "text" :text "hello"}]}))
            notifications (parsed-output writer)
            text-updates   (filter #(= "agent_message_chunk" (get-in % [:params :update :sessionUpdate])) notifications)
            text           (-> text-updates first (get-in [:params :update :content :text]))]
        (should= "end_turn" (get-in response [:result :stopReason]))
        (should= 1 (count text-updates))
        (should= "unknown crew: marvin\nuse /crew {name} to switch, or add marvin to config\n" text)))

    (it "catches unexpected exceptions and returns end_turn with error text"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (let [writer   (StringWriter.)
            response (with-redefs [single-turn/run-turn!
                                   (fn [& _] (throw (Exception. "something blew up")))]
                       (sut/dispatch-line (assoc prompt-opts :output-writer writer)
                                          (jrpc/request-line 12 "session/prompt"
                                                             {:sessionId "agent:main:acp:direct:user1"
                                                              :prompt [{:type "text" :text "hello"}]})))
            notifications (parsed-output writer)]
        (should= "end_turn" (get-in response [:result :stopReason]))
        (should-not (get-in response [:error]))
        (should (some #(= "agent_message_chunk" (get-in % [:params :update :sessionUpdate])) notifications))
        (should (some #(str/includes? (or (get-in % [:params :update :content :text]) "") "something blew up") notifications))))

    (it "writes a session/update text notification for slash commands"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (let [writer        (StringWriter.)
            result        (sut/dispatch-line (assoc prompt-opts :output-writer writer)
                                             (jrpc/request-line 40 "session/prompt"
                                                                {:sessionId "agent:main:acp:direct:user1"
                                                                 :prompt [{:type "text" :text "/status"}]}))
            notifications (parsed-output writer)]
        (should= "end_turn" (get-in result [:result :stopReason]))
        (should (some #(= "agent_message_chunk" (get-in % [:params :update :sessionUpdate])) notifications))
        (let [content (->> notifications
                           (map #(get-in % [:params :update :content :text]))
                           (remove nil?)
                           (str/join "\n"))]
          (should (re-find #"```text" content))
          (should (re-find #"Session Status" content))
          (should (re-find #"─+" content))
          (should (re-find #"Model\s+echo \(grover\)" content))
          (should (re-find #"Session\s+agent:main:acp:direct:user1" content))
          (should (re-find #"Soul\s+\".+\"" content))
          (should-not (re-find #"SOUL\.md" content)))))

    (it "switches crew members for ACP slash commands"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (let [agents        {"main"  {:name "main" :soul "You are Isaac." :model "grover"}
                           "ketch" {:name "ketch" :soul "You are a pirate." :model "grover"}}
            writer        (StringWriter.)
            result        (sut/dispatch-line {:state-dir     test-dir
                                              :crew-members        agents
                                              :models        test-models
                                              :output-writer writer}
                                             (jrpc/request-line 41 "session/prompt"
                                                                {:sessionId "agent:main:acp:direct:user1"
                                                                 :prompt [{:type "text" :text "/crew ketch"}]}))
            notifications (parsed-output writer)
            session       (storage/get-session test-dir "agent:main:acp:direct:user1")]
        (should= "end_turn" (get-in result [:result :stopReason]))
        (should= "ketch" (:crew session))
        (should-not (contains? session :agent))
        (should (some #(= "switched crew to ketch" (get-in % [:params :update :content :text])) notifications))))

    (it "switches models for ACP slash commands"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (let [models-with-alt (assoc test-models "grok" {:alias "grok" :model "grok-4-1-fast" :provider "grok" :context-window 32768})
            writer          (StringWriter.)
            result          (sut/dispatch-line {:state-dir     test-dir
                                                :crew-members        test-agents
                                                :models        models-with-alt
                                                :output-writer writer}
                                               (jrpc/request-line 42 "session/prompt"
                                                                  {:sessionId "agent:main:acp:direct:user1"
                                                                   :prompt [{:type "text" :text "/model grok"}]}))
            notifications   (parsed-output writer)
            session         (storage/get-session test-dir "agent:main:acp:direct:user1")]
        (should= "end_turn" (get-in result [:result :stopReason]))
        (should= "grok-4-1-fast" (:model session))
        (should= "grok" (:provider session))
        (should (some #(= "switched model to grok (grok/grok-4-1-fast)" (get-in % [:params :update :content :text])) notifications))))

  )

  (describe "session/cancel"

    (before
      (grover/reset-queue!)
      (tool-registry/clear!))

    (it "returns cancelled when interrupt is set before prompt starts"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (grover/enqueue! [{:type "text" :content "Hello" :model "echo"}])
      (sut/dispatch-line prompt-opts
                         (jrpc/notification-line "session/cancel"
                                                 {:sessionId "agent:main:acp:direct:user1"}))
      (let [result (-> (sut/dispatch-line prompt-opts
                                          (jrpc/request-line 30 "session/prompt"
                                                             {:sessionId "agent:main:acp:direct:user1"
                                                              :prompt [{:type "text" :text "Long task"}]}))
                       (as-> r (if (future? r) (deref r) r)))]
        (should= "cancelled" (get-in result [:result :stopReason]))))

    (it "returns cancelled when session/cancel interrupts an in-flight LLM request"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (grover/enable-delay!)
      (let [prompt (future
                     (sut/dispatch-line (assoc prompt-opts :output-writer (StringWriter.))
                                        (jrpc/request-line 31 "session/prompt"
                                                           {:sessionId "agent:main:acp:direct:user1"
                                                            :prompt [{:type "text" :text "think hard"}]})))]
        (grover/await-delay-start)
        (sut/dispatch-line prompt-opts
                           (jrpc/notification-line "session/cancel"
                                                   {:sessionId "agent:main:acp:direct:user1"}))
        (grover/release-delay!)
        (should= "cancelled" (get-in @prompt [:result :stopReason]))))

    (it "returns cancelled when session/cancel interrupts an in-flight exec tool"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (builtin/register-all! tool-registry/register!)
      (grover/enqueue! [{:tool_call "exec" :arguments {:command "sleep 30"}}])
      (let [exec-agents   {"main" {:name "main" :soul "You are Isaac." :model "grover" :tools {:allow ["exec"]}}}
            started (promise)
            release (promise)
            prompt  (future
                      (with-redefs [builtin/exec-tool
                                    (fn [{:keys [session-key]}]
                                      (deliver started true)
                                      @release
                                      (if (bridge/cancelled? session-key)
                                        {:error :cancelled}
                                        {:result "done"}))]
                        (sut/dispatch-line (assoc prompt-opts :crew-members exec-agents :output-writer (StringWriter.))
                                           (jrpc/request-line 32 "session/prompt"
                                                              {:sessionId "agent:main:acp:direct:user1"
                                                               :prompt [{:type "text" :text "run it"}]}))))]
        (should= true (deref started 1000 nil))
        (sut/dispatch-line prompt-opts
                           (jrpc/notification-line "session/cancel"
                                                   {:sessionId "agent:main:acp:direct:user1"}))
        (deliver release true)
        (should= "cancelled" (get-in (deref prompt 1000 nil) [:result :stopReason]))))

    (it "appends exactly one error entry when run-turn! throws an uncaught exception"
      (storage/create-session! test-dir "agent:main:acp:direct:user1")
      (with-redefs [single-turn/check-compaction! (fn [& _] (throw (RuntimeException. "forced failure")))]
        (sut/dispatch-line (assoc prompt-opts :output-writer (StringWriter.))
                           (jrpc/request-line 99 "session/prompt"
                                              {:sessionId "agent:main:acp:direct:user1"
                                               :prompt [{:type "text" :text "trigger crash"}]})))
      (let [transcript   (storage/get-transcript test-dir "agent:main:acp:direct:user1")
            error-entries (filter #(= "error" (:type %)) transcript)]
        (should= 1 (count error-entries))
        (should= "forced failure" (:content (first error-entries)))))

  )

)
