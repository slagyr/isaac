(ns isaac.cli.chat-spec
  (:require
    [clojure.java.io :as io]
    [isaac.cli.chat :as sut]
    [isaac.config.resolution :as config]
    [isaac.context.manager :as ctx]
    [isaac.session.storage :as storage]
    [speclj.core :refer :all]))

(def test-dir "target/test-chat")

(defn- clean-dir! [path]
  (let [dir (io/file path)]
    (when (.exists dir)
      (doseq [f (reverse (file-seq dir))]
        (.delete f)))))

(describe "CLI Chat"

  (before-all (clean-dir! test-dir))
  (after (clean-dir! test-dir))

  (describe "prepare"

    (it "returns a context map with defaults"
      (let [cfg {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}
                 :models {:providers [{:name    "ollama"
                                       :baseUrl "http://localhost:11434"
                                       :api     "ollama"}]}}
            ctx (sut/prepare {:agent "main"}
                             {:cfg  cfg
                              :sdir test-dir})]
        (should= "main" (:agent ctx))
        (should= "qwen3-coder:30b" (:model ctx))
        (should= "ollama" (:provider ctx))
        (should= test-dir (:state-dir ctx))
        (should-not-be-nil (:session-key ctx))
        (should-contain "You are Isaac" (:soul ctx))))

    (it "resolves model override"
      (let [cfg {:agents {:defaults {:model "ollama/default:7b"}}
                 :models {:providers [{:name    "ollama"
                                       :baseUrl "http://localhost:11434"
                                       :api     "ollama"}]}}
            ctx (sut/prepare {:agent "main" :model "ollama/override:13b"}
                             {:cfg  cfg
                              :sdir test-dir})]
        (should= "override:13b" (:model ctx))
        (should= "ollama" (:provider ctx))))

    (it "resolves model alias from overrides map"
      (let [cfg    {:agents {:defaults {:model "ollama/default:7b"}}
                    :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            models {"fast" {:model "llama3:8b" :provider "ollama" :contextWindow 8192}}
            ctx    (sut/prepare {:agent "main" :model "fast"}
                                {:cfg    cfg
                                 :sdir   test-dir
                                 :models models})]
        (should= "llama3:8b" (:model ctx))
        (should= "ollama" (:provider ctx))
        (should= 8192 (:context-window ctx))))

    (it "uses agent model when no override"
      (let [cfg    {:agents {:defaults {:model "ollama/default:7b"}}
                    :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            agents {"coder" {:model "ollama/codellama:34b"}}
            ctx    (sut/prepare {:agent "coder"}
                                {:cfg    cfg
                                 :sdir   test-dir
                                 :agents agents})]
        (should= "codellama:34b" (:model ctx))
        (should= "ollama" (:provider ctx))))

    (it "uses agent soul when available"
      (let [cfg    {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}
                    :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            agents {"soulful" {:soul "I am a custom soul." :model "ollama/qwen3-coder:30b"}}
            ctx    (sut/prepare {:agent "soulful"}
                                {:cfg    cfg
                                 :sdir   test-dir
                                 :agents agents})]
        (should= "I am a custom soul." (:soul ctx))))

    (it "creates a new session by default"
      (let [cfg {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}
                 :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            ctx (sut/prepare {:agent "main"}
                             {:cfg  cfg
                              :sdir test-dir})]
        (should-contain "agent:main:cli:direct:" (:session-key ctx))))

    (it "resumes a specific session by key"
      (let [cfg     {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}
                     :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            key-str "agent:main:cli:direct:testuser"
            _       (storage/create-session! test-dir key-str)
            ctx     (atom nil)]
        (with-out-str
          (reset! ctx (sut/prepare {:agent "main" :session key-str}
                                   {:cfg  cfg
                                    :sdir test-dir})))
        (should= key-str (:session-key @ctx))))

    (it "creates a new session when --session key doesn't exist"
      (let [cfg     {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}
                     :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            key-str "agent:main:cli:direct:nobody"
            ctx     (with-out-str
                      (sut/prepare {:agent "main" :session key-str}
                                   {:cfg  cfg
                                    :sdir test-dir}))]
        ;; Should not throw — creates the session
        (let [sessions (storage/list-sessions test-dir "main")]
          (should (some #(= key-str (:key %)) sessions)))))

    (it "resumes the most recent session with --resume"
      (let [cfg     {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}
                     :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            key-str "agent:main:cli:direct:resumeuser"
            _       (storage/create-session! test-dir key-str)
            ctx     (atom nil)]
        (with-out-str
          (reset! ctx (sut/prepare {:agent "main" :resume true}
                                   {:cfg  cfg
                                    :sdir test-dir})))
        (should-contain "cli" (:session-key @ctx))))

    (it "falls back to default context-window"
      (let [cfg {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}
                 :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            ctx (sut/prepare {:agent "main"}
                             {:cfg  cfg
                              :sdir test-dir})]
        (should= 32768 (:context-window ctx))))

    (it "resolves agents.models alias for non-provider/model format"
      (let [cfg {:agents {:defaults {:model "fast"}
                          :models   {:fast {:model    "llama3:8b"
                                            :provider "ollama"}}}
                 :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            ctx (sut/prepare {:agent "main"}
                             {:cfg  cfg
                              :sdir test-dir})]
        (should= "llama3:8b" (:model ctx))
        (should= "ollama" (:provider ctx)))))

  (describe "build-chat-request"

    (it "builds request for ollama provider"
      (let [result (sut/build-chat-request "ollama" {}
                     {:model      "qwen:7b"
                      :soul       "You are helpful."
                      :transcript [{:type "message" :message {:role "user" :content "hi"}}]})]
        (should= "qwen:7b" (:model result))
        (should-not-be-nil (:messages result))
        (should-be-nil (:system result))))

    (it "includes tool definitions when tools are provided"
      (let [tools  [{:name "read" :description "Read a file" :parameters {}}]
            result (sut/build-chat-request "ollama" {}
                     {:model      "qwen:7b"
                      :soul       "You are helpful."
                      :transcript [{:type "message" :message {:role "user" :content "hi"}}]
                      :tools      tools})]
        (should-not-be-nil (:tools result))
        (should= 1 (count (:tools result)))))

    (it "omits tools key when no tools are provided"
      (let [result (sut/build-chat-request "ollama" {}
                     {:model      "qwen:7b"
                      :soul       "You are helpful."
                      :transcript [{:type "message" :message {:role "user" :content "hi"}}]})]
        (should-be-nil (:tools result))))

    (it "builds request for anthropic provider with system"
      (let [result (sut/build-chat-request "anthropic" {}
                     {:model      "claude-sonnet-4-20250514"
                      :soul       "You are helpful."
                      :transcript [{:type "message" :message {:role "user" :content "hi"}}]})]
        (should= "claude-sonnet-4-20250514" (:model result))
        (should-not-be-nil (:messages result))
        (should-not-be-nil (:system result))
        (should-not-be-nil (:max_tokens result)))))

  (describe "extract-tokens"

    (it "extracts tokens from anthropic-style response"
      (let [result {:content  "hello"
                    :response {:usage {:inputTokens  100
                                       :outputTokens 50
                                       :cacheRead    10
                                       :cacheWrite   5}}}
            tokens (sut/extract-tokens result)]
        (should= 100 (:inputTokens tokens))
        (should= 50 (:outputTokens tokens))
        (should= 10 (:cacheRead tokens))
        (should= 5 (:cacheWrite tokens))))

    (it "extracts tokens from ollama-style response"
      (let [result {:content  "hello"
                    :response {:prompt_eval_count 200
                               :eval_count        80}}
            tokens (sut/extract-tokens result)]
        (should= 200 (:inputTokens tokens))
        (should= 80 (:outputTokens tokens))))

    (it "defaults to zero when no usage data"
      (let [result {:content "hello" :response {}}
            tokens (sut/extract-tokens result)]
        (should= 0 (:inputTokens tokens))
        (should= 0 (:outputTokens tokens))
        (should-be-nil (:cacheRead tokens))
        (should-be-nil (:cacheWrite tokens)))))

  (describe "process-response!"

    (it "appends assistant message and updates tokens on success"
      (let [key-str "agent:main:cli:direct:testuser"
            _       (storage/create-session! test-dir key-str)
            result  {:content  "I can help!"
                     :response {:usage {:inputTokens 50 :outputTokens 20}}}]
        (sut/process-response! test-dir key-str result {:model "qwen:7b" :provider "ollama"})
        (let [transcript (storage/get-transcript test-dir key-str)
              messages   (filter #(= "message" (:type %)) transcript)
              last-msg   (last messages)]
          (should= "assistant" (get-in last-msg [:message :role]))
          (should= "I can help!" (get-in last-msg [:message :content])))))

    (it "stores the provider-returned model in the transcript"
      (let [key-str "agent:main:cli:direct:model-test"
            _       (storage/create-session! test-dir key-str)
            result  {:content  "Hello!"
                     :response {:model "gpt-5-20250714"
                                :usage {:inputTokens 10 :outputTokens 5}}}]
        (sut/process-response! test-dir key-str result {:model "gpt-5" :provider "openai"})
        (let [transcript (storage/get-transcript test-dir key-str)
              messages   (filter #(= "message" (:type %)) transcript)
              last-msg   (last messages)]
          (should= "gpt-5-20250714" (get-in last-msg [:message :model])))))

    (it "falls back to configured model when provider returns no model"
      (let [key-str "agent:main:cli:direct:fallback-test"
            _       (storage/create-session! test-dir key-str)
            result  {:content  "Hello!"
                     :response {:usage {:inputTokens 10 :outputTokens 5}}}]
        (sut/process-response! test-dir key-str result {:model "qwen:7b" :provider "ollama"})
        (let [transcript (storage/get-transcript test-dir key-str)
              messages   (filter #(= "message" (:type %)) transcript)
              last-msg   (last messages)]
          (should= "qwen:7b" (get-in last-msg [:message :model])))))

    (it "prints error on failure"
      (let [output (with-out-str
                     (sut/process-response! test-dir "agent:x:cli:direct:x"
                                            {:error true :message "API timeout"}
                                            {:model "m" :provider "p"}))]
        (should-contain "Error: API timeout" output))))

  (describe "check-compaction!"

    (it "does not compact when under context window"
      (let [key-str  "agent:main:cli:direct:comptest"
            _        (storage/create-session! test-dir key-str)
            compacted (atom false)]
        (with-redefs [ctx/should-compact? (constantly false)
                      ctx/compact!        (fn [& _] (reset! compacted true))]
          (sut/check-compaction! test-dir key-str
                                 {:model "m" :soul "s" :context-window 32768
                                  :provider "ollama" :provider-config {}})
          (should= false @compacted))))

    (it "compacts when over context window"
      (let [key-str  "agent:main:cli:direct:comptest2"
            _        (storage/create-session! test-dir key-str)
            compacted (atom false)]
        (with-redefs [ctx/should-compact? (constantly true)
                      ctx/compact!        (fn [& _] (reset! compacted true))]
          (with-out-str
            (sut/check-compaction! test-dir key-str
                                   {:model "m" :soul "s" :context-window 32768
                                    :provider "ollama" :provider-config {}}))
          (should= true @compacted)))))

  (describe "print-streaming-response"

    (it "accumulates streamed content and returns result"
      (with-redefs [sut/dispatch-chat-stream (fn [_ _ _ on-chunk]
                                               (on-chunk {:message {:content "Hello"}})
                                               (on-chunk {:message {:content " world"} :done true})
                                               {:message {:role "assistant" :content "Hello world"}})]
        (let [output (atom nil)
              result (with-out-str
                       (reset! output (sut/print-streaming-response "ollama" {} {})))]
          (should= "Hello world" (:content @output))
          (should-contain "Hello world" result))))

    (it "returns error map on stream failure"
      (let [captured (atom nil)]
        (with-redefs [sut/dispatch-chat-stream (fn [_ _ _ _] {:error :connection-refused :message "fail"})]
          (with-out-str
            (reset! captured (sut/print-streaming-response "ollama" {} {})))
          (should= :connection-refused (:error @captured)))))

    (it "extracts content from anthropic-style delta chunks"
      (with-redefs [sut/dispatch-chat-stream (fn [_ _ _ on-chunk]
                                               (on-chunk {:delta {:text "Hi"}})
                                               (on-chunk {:delta {:text "!"} :done true})
                                               {:message {:role "assistant" :content "Hi!"}})]
        (let [captured (atom nil)]
          (with-out-str
            (reset! captured (sut/print-streaming-response "anthropic" {} {})))
          (should= "Hi!" (:content @captured)))))

    (it "extracts content from openai-style delta chunks"
      (with-redefs [sut/dispatch-chat-stream (fn [_ _ _ on-chunk]
                                               (on-chunk {:choices [{:delta {:content "Hey"}}]})
                                               {:message {:role "assistant" :content "Hey"}})]
        (let [captured (atom nil)]
          (with-out-str
            (reset! captured (sut/print-streaming-response "openai" {} {})))
          (should= "Hey" (:content @captured)))))

    (it "uses result message content when no streaming content"
      (with-redefs [sut/dispatch-chat-stream (fn [_ _ _ _]
                                               {:message {:role "assistant" :content "fallback"}})]
        (let [captured (atom nil)]
          (with-out-str
            (reset! captured (sut/print-streaming-response "ollama" {} {})))
          (should= "fallback" (:content @captured))))))

  (describe "dispatch-chat-with-tools"

    (it "calls the provider chat-with-tools and returns result"
      (with-redefs [sut/dispatch-chat (fn [_ _ req]
                                        {:message    {:role "assistant" :content "done"}
                                         :tool-calls []
                                         :model      "echo"})]
        (let [tool-fn (fn [_ _] "tool result")
              result  (sut/dispatch-chat-with-tools "ollama" {} {:model "echo" :messages []} tool-fn)]
          (should-not (:error result))))))

  (describe "run-tool-calls!"

    (it "stores tool calls and results in the transcript"
      (let [key-str "agent:main:cli:direct:tooltest"
            _       (storage/create-session! test-dir key-str)
            tool-calls [{:id "tc-1" :name "echo" :type "toolCall" :arguments {:msg "hi"}}]
            tool-fn    (fn [_ _] "echo result")]
        (sut/run-tool-calls! test-dir key-str tool-calls tool-fn)
        (let [transcript (storage/get-transcript test-dir key-str)
              messages   (filter #(= "message" (:type %)) transcript)]
          (should= 2 (count messages))
          (should= "assistant"  (get-in (first messages) [:message :role]))
          (should= "toolResult" (get-in (second messages) [:message :role])))))

    (it "marks tool results as errors when tool-fn returns an error string"
      (let [key-str "agent:main:cli:direct:toolerr"
            _       (storage/create-session! test-dir key-str)
            tool-calls [{:id "tc-1" :name "boom" :type "toolCall" :arguments {}}]
            tool-fn    (fn [_ _] "Error: something went wrong")]
        (sut/run-tool-calls! test-dir key-str tool-calls tool-fn)
        (let [transcript (storage/get-transcript test-dir key-str)
              tool-result (second (filter #(= "message" (:type %)) transcript))]
          (should= true (get-in tool-result [:message :isError]))))))

  )
