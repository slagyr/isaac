(ns isaac.cli.chat-spec
  (:require
    [clojure.java.io :as io]
    [isaac.llm.anthropic :as anthropic]
    [isaac.llm.claude-sdk :as claude-sdk]
    [isaac.llm.ollama :as ollama]
    [isaac.llm.openai-compat :as openai-compat]
    [isaac.logger :as log]
    [isaac.cli.chat.dispatch :as dispatch]
    [isaac.session.logging :as logging]
    [isaac.cli.chat.loop :as chat-loop]
    [isaac.cli.chat.single-turn :as single-turn]
    [isaac.config.resolution :as config]
    [isaac.context.manager :as ctx]
    [isaac.session.storage :as storage]
    [isaac.spec-helper :as helper]
    [isaac.tool.registry :as tool-registry]
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
            ctx (chat-loop/prepare {:agent "main"}
                             {:cfg  cfg
                              :sdir test-dir})]
        (should= "main" (:agent ctx))
        (should= "qwen3-coder:30b" (:model ctx))
        (should= "ollama" (:provider ctx))
        (should= test-dir (:state-dir ctx))
        (should-not-be-nil (:session-key ctx))
        (should-not-be-nil (:soul ctx))))

    (it "resolves model override"
      (let [cfg {:agents {:defaults {:model "ollama/default:7b"}}
                 :models {:providers [{:name    "ollama"
                                       :baseUrl "http://localhost:11434"
                                       :api     "ollama"}]}}
            ctx (chat-loop/prepare {:agent "main" :model "ollama/override:13b"}
                             {:cfg  cfg
                               :sdir test-dir})]
        (should= "override:13b" (:model ctx))
        (should= "ollama" (:provider ctx))))

    (it "uses provider base-url and context window for provider/model refs"
      (let [cfg {:agents {:defaults {:model "openai/gpt-5"}}
                 :models {:providers [{:name          "openai"
                                       :baseUrl       "https://api.openai.com/v1"
                                       :contextWindow 128000
                                       :api           "openai-compatible"}]}}
            ctx (chat-loop/prepare {:agent "main"}
                             {:cfg  cfg
                              :sdir test-dir})]
        (should= "gpt-5" (:model ctx))
        (should= "openai" (:provider ctx))
        (should= "https://api.openai.com/v1" (:base-url ctx))
        (should= 128000 (:context-window ctx))))

    (it "resolves model alias from overrides map"
      (let [cfg    {:agents {:defaults {:model "ollama/default:7b"}}
                    :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            models {"fast" {:model "llama3:8b" :provider "ollama" :contextWindow 8192}}
            ctx    (chat-loop/prepare {:agent "main" :model "fast"}
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
            ctx    (chat-loop/prepare {:agent "coder"}
                                {:cfg    cfg
                                 :sdir   test-dir
                                 :agents agents})]
        (should= "codellama:34b" (:model ctx))
        (should= "ollama" (:provider ctx))))

    (it "uses agent soul when available"
      (let [cfg    {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}
                    :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            agents {"soulful" {:soul "I am a custom soul." :model "ollama/qwen3-coder:30b"}}
            ctx    (chat-loop/prepare {:agent "soulful"}
                                {:cfg    cfg
                                 :sdir   test-dir
                                 :agents agents})]
        (should= "I am a custom soul." (:soul ctx))))

    (it "creates a new session by default"
      (let [cfg {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}
                 :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            ctx (chat-loop/prepare {:agent "main"}
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
          (reset! ctx (chat-loop/prepare {:agent "main" :session key-str}
                                   {:cfg  cfg
                                    :sdir test-dir})))
        (should= key-str (:session-key @ctx))))

    (it "creates a new session when --session key doesn't exist"
      (let [cfg     {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}
                     :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            key-str "agent:main:cli:direct:nobody"
            ctx     (with-out-str
                      (chat-loop/prepare {:agent "main" :session key-str}
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
          (reset! ctx (chat-loop/prepare {:agent "main" :resume true}
                                   {:cfg  cfg
                                     :sdir test-dir})))
        (should-contain "cli" (:session-key @ctx))))

    (it "reports only message entries when resuming a session"
      (let [cfg     {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}
                     :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            key-str "agent:main:cli:direct:countuser"
            _       (storage/create-session! test-dir key-str)
            _       (storage/append-message! test-dir key-str {:role "user" :content "hello"})
            _       (storage/append-compaction! test-dir key-str {:summary "short" :tokensBefore 10})
            output  (with-out-str
                      (chat-loop/prepare {:agent "main" :resume true}
                                   {:cfg  cfg
                                    :sdir test-dir}))]
        (should-contain "1 messages" output)))

    (it "falls back to default context-window"
      (let [cfg {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}
                 :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            ctx (chat-loop/prepare {:agent "main"}
                             {:cfg  cfg
                               :sdir test-dir})]
        (should= 32768 (:context-window ctx))))

    (it "falls back to default ollama config for unqualified model refs"
      (let [cfg {:agents {:defaults {:model "qwen3-coder:30b"}}
                 :models {:providers [{:name "ollama"}]}}
            ctx (chat-loop/prepare {:agent "main"}
                             {:cfg  cfg
                              :sdir test-dir})]
        (should= "qwen3-coder:30b" (:model ctx))
        (should= "ollama" (:provider ctx))
        (should= "http://localhost:11434" (:base-url ctx))
        (should= 32768 (:context-window ctx))))

    (it "resolves agents.models alias for non-provider/model format"
      (let [cfg {:agents {:defaults {:model "fast"}
                          :models   {:fast {:model    "llama3:8b"
                                            :provider "ollama"}}}
                 :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            ctx (chat-loop/prepare {:agent "main"}
                             {:cfg  cfg
                              :sdir test-dir})]
        (should= "llama3:8b" (:model ctx))
        (should= "ollama" (:provider ctx)))))

  (describe "build-chat-request"

    (it "builds request for ollama provider"
      (let [result (single-turn/build-chat-request "ollama" {}
                     {:model      "qwen:7b"
                      :soul       "You are helpful."
                      :transcript [{:type "message" :message {:role "user" :content "hi"}}]})]
        (should= "qwen:7b" (:model result))
        (should-not-be-nil (:messages result))
        (should-be-nil (:system result))))

    (it "includes tool definitions when tools are provided"
      (let [tools  [{:name "read" :description "Read a file" :parameters {}}]
            result (single-turn/build-chat-request "ollama" {}
                     {:model      "qwen:7b"
                      :soul       "You are helpful."
                      :transcript [{:type "message" :message {:role "user" :content "hi"}}]
                      :tools      tools})]
        (should-not-be-nil (:tools result))
        (should= 1 (count (:tools result)))))

    (it "omits tools key when no tools are provided"
      (let [result (single-turn/build-chat-request "ollama" {}
                     {:model      "qwen:7b"
                      :soul       "You are helpful."
                      :transcript [{:type "message" :message {:role "user" :content "hi"}}]})]
        (should-be-nil (:tools result))))

    (it "builds request for anthropic provider with system"
      (let [result (single-turn/build-chat-request "anthropic" {}
                     {:model      "claude-sonnet-4-20250514"
                      :soul       "You are helpful."
                      :transcript [{:type "message" :message {:role "user" :content "hi"}}]})]
        (should= "claude-sonnet-4-20250514" (:model result))
        (should-not-be-nil (:messages result))
        (should-not-be-nil (:system result))
        (should-not-be-nil (:max_tokens result)))))

  (describe "private helpers"

    (it "parses model refs when no alias is configured"
      (let [cfg    {:agents {:defaults {:model "openai/gpt-5"}}
                    :models {:providers [{:name "openai" :baseUrl "https://api.openai.com/v1"}]}}
            result (@#'chat-loop/resolve-model-info cfg {} nil)]
        (should= "gpt-5" (:model result))
        (should= "openai" (:provider result))))

    (it "resolves the ollama api explicitly"
      (should= "ollama" (@#'dispatch/resolve-api "ollama" {})))

    (it "marks tool results as errors when the result starts with Error"
      (let [messages (atom [])]
        (with-redefs [storage/append-message! (fn [_ _ message] (swap! messages conj message))]
          (single-turn/run-tool-calls! test-dir "agent:main:cli:direct:toolerr"
                               [[{:id "tc-1" :name "boom" :type "toolCall" :arguments {}}
                                 "Error: something went wrong"]])
          (should= true (:isError (second @messages))))))

    (it "processes non-blank input and ignores blank input"
      (let [calls (atom [])]
        (with-redefs [single-turn/process-user-input! (fn [_ _ input _] (swap! calls conj input))]
          (@#'chat-loop/maybe-process-input! test-dir "agent:main:cli:direct:test" "hello" {})
          (@#'chat-loop/maybe-process-input! test-dir "agent:main:cli:direct:test" "   " {})
          (should= ["hello"] @calls)))))

  (describe "dispatch-chat"

    (helper/with-captured-logs)

    (it "dispatches claude-sdk requests and logs success"
      (with-redefs [claude-sdk/chat (fn [_] {:model "sonnet" :message {:role "assistant" :content "hi"}})]
        (let [result (dispatch/dispatch-chat "claude-sdk" {} {:model "m" :messages []})]
          (should= "sonnet" (:model result))
          (should= [:chat/request :chat/response] (mapv :event @log/captured-logs)))))

    (it "dispatches openai-compatible errors and logs them"
      (with-redefs [openai-compat/chat (fn [_ _] {:error :auth-failed :status 401})]
        (let [result (dispatch/dispatch-chat "openai" {:api "openai-compatible"} {:model "m" :messages []})]
          (should= :auth-failed (:error result))
          (should= [:chat/request :chat/error] (mapv :event @log/captured-logs))))))

  (describe "dispatch-chat-stream"

    (helper/with-captured-logs)

    (it "dispatches ollama stream requests and logs success"
      (let [chunks (atom [])]
        (with-redefs [ollama/chat-stream (fn [_ on-chunk _]
                                           (on-chunk {:message {:content "hi"}})
                                           {:model "qwen" :message {:role "assistant" :content "hi"}})]
          (let [result (dispatch/dispatch-chat-stream "ollama" {} {:model "m" :messages []}
                                                 #(swap! chunks conj %))]
            (should= "qwen" (:model result))
            (should= 1 (count @chunks))
            (should= [:chat/stream-request :chat/stream-response] (mapv :event @log/captured-logs))))))

    (it "dispatches anthropic stream errors and logs them"
      (with-redefs [anthropic/chat-stream (fn [_ _ _] {:error :connection-refused})]
        (let [result (dispatch/dispatch-chat-stream "anthropic" {:api "anthropic-messages"} {:model "m" :messages []} identity)]
          (should= :connection-refused (:error result))
          (should= [:chat/stream-request :chat/stream-error] (mapv :event @log/captured-logs))))))

  (describe "extract-tokens"

    (it "extracts tokens from anthropic-style response"
      (let [result {:content  "hello"
                    :response {:usage {:inputTokens  100
                                       :outputTokens 50
                                       :cacheRead    10
                                       :cacheWrite   5}}}
            tokens (single-turn/extract-tokens result)]
        (should= 100 (:inputTokens tokens))
        (should= 50 (:outputTokens tokens))
        (should= 10 (:cacheRead tokens))
        (should= 5 (:cacheWrite tokens))))

    (it "extracts tokens from ollama-style response"
      (let [result {:content  "hello"
                    :response {:prompt_eval_count 200
                               :eval_count        80}}
            tokens (single-turn/extract-tokens result)]
        (should= 200 (:inputTokens tokens))
        (should= 80 (:outputTokens tokens))))

    (it "defaults to zero when no usage data"
      (let [result {:content "hello" :response {}}
            tokens (single-turn/extract-tokens result)]
        (should= 0 (:inputTokens tokens))
        (should= 0 (:outputTokens tokens))
        (should-be-nil (:cacheRead tokens))
        (should-be-nil (:cacheWrite tokens)))))

  (describe "process-response!"

    (helper/with-captured-logs)

    (it "appends assistant message and updates tokens on success"
      (let [key-str "agent:main:cli:direct:testuser"
            _       (storage/create-session! test-dir key-str)
            result  {:content  "I can help!"
                     :response {:usage {:inputTokens 50 :outputTokens 20}}}]
        (single-turn/process-response! test-dir key-str result {:model "qwen:7b" :provider "ollama"})
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
        (single-turn/process-response! test-dir key-str result {:model "gpt-5" :provider "openai"})
        (let [transcript (storage/get-transcript test-dir key-str)
              messages   (filter #(= "message" (:type %)) transcript)
              last-msg   (last messages)]
          (should= "gpt-5-20250714" (get-in last-msg [:message :model])))))

    (it "falls back to configured model when provider returns no model"
      (let [key-str "agent:main:cli:direct:fallback-test"
            _       (storage/create-session! test-dir key-str)
            result  {:content  "Hello!"
                     :response {:usage {:inputTokens 10 :outputTokens 5}}}]
        (single-turn/process-response! test-dir key-str result {:model "qwen:7b" :provider "ollama"})
        (let [transcript (storage/get-transcript test-dir key-str)
              messages   (filter #(= "message" (:type %)) transcript)
              last-msg   (last messages)]
          (should= "qwen:7b" (get-in last-msg [:message :model])))))

    (it "returns error result on failure"
      (let [result (single-turn/process-response! test-dir "agent:x:cli:direct:x"
                                          {:error true :message "API timeout"}
                                          {:model "m" :provider "p"})]
        (should= true (:error result))
        (should= "API timeout" (:message result))))

    (it "records error entries in transcript when llm call fails"
      (let [key-str "agent:main:cli:direct:error-test"
            _      (storage/create-session! test-dir key-str)
            _      (single-turn/process-response! test-dir key-str
                                          {:error :connection-refused :message "refused"}
                                          {:model "qwen:7b" :provider "ollama"})
            transcript (storage/get-transcript test-dir key-str)
            messages   (filter #(= "message" (:type %)) transcript)
            last-msg   (last messages)]
        (should= "error" (get-in last-msg [:message :role]))
        (should= ":connection-refused" (get-in last-msg [:message :error]))
        (should= "refused" (get-in last-msg [:message :content]))
        (should= "qwen:7b" (get-in last-msg [:message :model]))
        (should= "ollama" (get-in last-msg [:message :provider]))))

    (it "returns body error details in result when message is absent"
      (let [result (single-turn/process-response! test-dir "agent:x:cli:direct:x"
                                          {:error  :api-error
                                           :status 400
                                           :body   {:error {:type "invalid_request_error"
                                                            :message "Bad request"}}}
                                          {:model "m" :provider "p"})]
        (should= :api-error (:error result))
        (should= 400 (:status result))))

    (it "returns http status error in result when only status is available"
      (let [result (single-turn/process-response! test-dir "agent:x:cli:direct:x"
                                          {:error  :api-error
                                           :status 503}
                                          {:model "m" :provider "p"})]
        (should= :api-error (:error result))
        (should= 503 (:status result))))

    (it "returns nil on success"
      (let [key-str "agent:main:cli:direct:success-ret"
            _       (storage/create-session! test-dir key-str)
            result  (single-turn/process-response! test-dir key-str
                                           {:content "Hello!" :response {:usage {:inputTokens 5 :outputTokens 3}}}
                                           {:model "m" :provider "p"})]
        (should-be-nil result)))

    (it "logs :chat/response-failed at error with session and provider on error"
      (single-turn/process-response! test-dir "agent:x:cli:direct:x"
                             {:error :connection-refused}
                             {:model "m" :provider "ollama"})
      (let [entry (first (filter #(= :chat/response-failed (:event %)) @log/captured-logs))]
        (should-not-be-nil entry)
        (should= :error (:level entry))
        (should= "ollama" (:provider entry))
        (should= "agent:x:cli:direct:x" (:session entry))))

    (it "logs :session/message-stored at debug with session and model on success"
      (let [key-str "agent:main:cli:direct:log-test"
            _       (storage/create-session! test-dir key-str)]
        (single-turn/process-response! test-dir key-str
                               {:content  "Hello!"
                                :response {:model "grover" :usage {:inputTokens 10 :outputTokens 5}}}
                               {:model "grover" :provider "grover"})
        (let [entry (first (filter #(= :session/message-stored (:event %)) @log/captured-logs))]
          (should-not-be-nil entry)
          (should= :debug (:level entry))
          (should= key-str (:session entry))
          (should= "grover" (:model entry)))))

  (describe "log-stream-completed!"

    (helper/with-captured-logs)

    (it "logs :session/stream-completed at debug with session"
      (logging/log-stream-completed! "agent:x:cli:direct:x")
      (let [entry (first @log/captured-logs)]
        (should= :debug (:level entry))
        (should= :session/stream-completed (:event entry))
        (should= "agent:x:cli:direct:x" (:session entry)))))

  (describe "check-compaction!"

    (helper/with-captured-logs)

    (it "does not compact when under context window"
      (let [key-str  "agent:main:cli:direct:comptest"
            _        (storage/create-session! test-dir key-str)
            compacted (atom false)]
        (with-redefs [ctx/should-compact? (constantly false)
                      ctx/compact!        (fn [& _] (reset! compacted true))]
          (single-turn/check-compaction! test-dir key-str
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
            (single-turn/check-compaction! test-dir key-str
                                   {:model "m" :soul "s" :context-window 32768
                                    :provider "ollama" :provider-config {}}))
          (should= true @compacted)))))

    (it "passes the matching session entry to compaction checks"
      (let [checked-entry (atom nil)]
        (with-redefs [storage/list-sessions (fn [_ _]
                                              [{:key "agent:main:cli:direct:other" :context-window 1}
                                               {:key "agent:main:cli:direct:target" :context-window 2}])
                      ctx/should-compact?  (fn [entry _]
                                             (reset! checked-entry entry)
                                             false)]
          (single-turn/check-compaction! test-dir "agent:main:cli:direct:target"
                                 {:model "m" :soul "s" :context-window 32768
                                  :provider "ollama" :provider-config {}})
          (should= "agent:main:cli:direct:target" (:key @checked-entry)))))

    (it "logs :session/compaction-check at debug with session, provider, model, totalTokens, contextWindow"
      (let [key-str "agent:main:cli:direct:checklog"
            _       (storage/create-session! test-dir key-str)
            _       (storage/update-tokens! test-dir key-str {:inputTokens 50 :outputTokens 0})]
        (with-redefs [ctx/should-compact? (constantly false)]
          (single-turn/check-compaction! test-dir key-str
                                 {:model "echo" :soul "s" :context-window 100
                                  :provider "grover" :provider-config {}}))
        (let [entry (first (filter #(= :session/compaction-check (:event %)) @log/captured-logs))]
          (should-not-be-nil entry)
          (should= :debug (:level entry))
          (should= key-str (:session entry))
          (should= "grover" (:provider entry))
          (should= "echo" (:model entry))
          (should= 50 (:totalTokens entry))
          (should= 100 (:contextWindow entry)))))

    (it "logs :session/compaction-started at info when compaction triggers"
      (let [key-str "agent:main:cli:direct:startlog"
            _       (storage/create-session! test-dir key-str)
            _       (storage/update-tokens! test-dir key-str {:inputTokens 50 :outputTokens 0})]
        (with-redefs [ctx/should-compact? (constantly true)
                      ctx/compact!        (fn [& _] nil)]
          (with-out-str
            (single-turn/check-compaction! test-dir key-str
                                   {:model "echo" :soul "s" :context-window 100
                                    :provider "grover" :provider-config {}})))
        (let [entry (first (filter #(= :session/compaction-started (:event %)) @log/captured-logs))]
          (should-not-be-nil entry)
          (should= :info (:level entry))
          (should= key-str (:session entry))
          (should= "grover" (:provider entry))
          (should= "echo" (:model entry))
          (should= 50 (:totalTokens entry))
          (should= 100 (:contextWindow entry)))))

    (it "does not log :session/compaction-started when under threshold"
      (let [key-str "agent:main:cli:direct:nolog"
            _       (storage/create-session! test-dir key-str)]
        (with-redefs [ctx/should-compact? (constantly false)]
          (single-turn/check-compaction! test-dir key-str
                                 {:model "m" :soul "s" :context-window 100
                                  :provider "grover" :provider-config {}}))
        (let [entry (first (filter #(= :session/compaction-started (:event %)) @log/captured-logs))]
          (should-be-nil entry))))

    (it "logs :session/compaction-failed at error when compact! returns an error"
      (let [key-str "agent:main:cli:direct:faillog"
            _       (storage/create-session! test-dir key-str)]
        (with-redefs [ctx/should-compact? (constantly true)
                      ctx/compact!        (fn [& _] {:error :llm-error :message "context length exceeded"})]
          (with-out-str
            (single-turn/check-compaction! test-dir key-str
                                   {:model "m" :soul "s" :context-window 100
                                    :provider "grover" :provider-config {}})))
        (let [entry (first (filter #(= :session/compaction-failed (:event %)) @log/captured-logs))]
          (should-not-be-nil entry)
          (should= :error (:level entry))
          (should= key-str (:session entry)))))

    (it "repeats compaction until the session no longer exceeds the context window"
      (let [key-str   "agent:main:cli:direct:repeatloop"
            _         (storage/create-session! test-dir key-str)
            _         (storage/update-session! test-dir key-str {:totalTokens 62})
            attempts  (atom 0)]
        (with-redefs [ctx/compact! (fn [sdir compact-key _]
                                     (swap! attempts inc)
                                     (storage/update-session! sdir compact-key
                                                              {:totalTokens (case @attempts
                                                                              1 40
                                                                              2 20)})
                                     {:type "compaction"})]
          (with-out-str
            (single-turn/check-compaction! test-dir key-str
                                   {:model "qwen3-coder:30b" :soul "You are Isaac." :context-window 32
                                    :provider "grover" :provider-config {}})))
        (should= 2 @attempts)))

    (it "stops repeated compaction when token usage does not decrease"
      (let [key-str  "agent:main:cli:direct:noprogress"
            _        (storage/create-session! test-dir key-str)
            _        (storage/update-session! test-dir key-str {:totalTokens 62})
            attempts (atom 0)]
        (with-redefs [ctx/compact! (fn [sdir compact-key _]
                                     (swap! attempts inc)
                                     (storage/update-session! sdir compact-key {:totalTokens 62})
                                     {:type "compaction"})]
          (with-out-str
            (single-turn/check-compaction! test-dir key-str
                                   {:model "qwen3-coder:30b" :soul "You are Isaac." :context-window 32
                                    :provider "grover" :provider-config {}})))
        (should= 1 @attempts))))

  (describe "print-streaming-response"

    (it "accumulates streamed content and returns result"
      (with-redefs [dispatch/dispatch-chat-stream (fn [_ _ _ on-chunk]
                                               (on-chunk {:message {:content "Hello"}})
                                               (on-chunk {:message {:content " world"} :done true})
                                               {:message {:role "assistant" :content "Hello world"}})]
        (let [output (atom nil)
              result (with-out-str
                       (reset! output (single-turn/print-streaming-response "ollama" {} {})))]
          (should= "Hello world" (:content @output))
          (should-contain "Hello world" result))))

    (it "returns error map on stream failure"
      (let [captured (atom nil)]
        (with-redefs [dispatch/dispatch-chat-stream (fn [_ _ _ _] {:error :connection-refused :message "fail"})]
          (with-out-str
            (reset! captured (single-turn/print-streaming-response "ollama" {} {})))
          (should= :connection-refused (:error @captured)))))

    (it "extracts content from anthropic-style delta chunks"
      (with-redefs [dispatch/dispatch-chat-stream (fn [_ _ _ on-chunk]
                                               (on-chunk {:delta {:text "Hi"}})
                                               (on-chunk {:delta {:text "!"} :done true})
                                               {:message {:role "assistant" :content "Hi!"}})]
        (let [captured (atom nil)]
          (with-out-str
            (reset! captured (single-turn/print-streaming-response "anthropic" {} {})))
          (should= "Hi!" (:content @captured)))))

    (it "extracts content from openai-style delta chunks"
      (with-redefs [dispatch/dispatch-chat-stream (fn [_ _ _ on-chunk]
                                                (on-chunk {:choices [{:delta {:content "Hey"}}]})
                                                {:message {:role "assistant" :content "Hey"}})]
        (let [captured (atom nil)]
          (with-out-str
            (reset! captured (single-turn/print-streaming-response "openai" {} {})))
          (should= "Hey" (:content @captured)))))

    (it "prefers openai streamed delta content over fallback result content"
      (with-redefs [dispatch/dispatch-chat-stream (fn [_ _ _ on-chunk]
                                               (on-chunk {:choices [{:delta {:content "streamed"}}]})
                                               {:message {:role "assistant" :content "fallback"}})]
        (let [captured (atom nil)]
          (with-out-str
            (reset! captured (single-turn/print-streaming-response "openai" {} {})))
          (should= "streamed" (:content @captured)))))

    (it "uses result message content when no streaming content"
      (with-redefs [dispatch/dispatch-chat-stream (fn [_ _ _ _]
                                               {:message {:role "assistant" :content "fallback"}})]
        (let [captured (atom nil)]
          (with-out-str
            (reset! captured (single-turn/print-streaming-response "ollama" {} {})))
          (should= "fallback" (:content @captured)))))

    (it "keeps the done chunk as the final response"
      (with-redefs [dispatch/dispatch-chat-stream (fn [_ _ _ on-chunk]
                                               (on-chunk {:message {:content "Hello"}})
                                               (on-chunk {:done true :message {:content " world"} :status :finished})
                                               {:message {:role "assistant" :content "ignored"}})]
        (let [captured (atom nil)]
          (with-out-str
            (reset! captured (single-turn/print-streaming-response "ollama" {} {})))
          (should= :finished (get-in @captured [:response :status])))))

    (it "does not duplicate a final done chunk that repeats the full content"
      (with-redefs [dispatch/dispatch-chat-stream (fn [_ _ _ on-chunk]
                                               (on-chunk {:message {:content "README "}})
                                               (on-chunk {:done true :message {:content "README summary"}})
                                               {:message {:role "assistant" :content "README summary"}})]
        (let [captured (atom nil)]
          (with-out-str
            (reset! captured (single-turn/print-streaming-response "grover" {} {})))
          (should= "README summary" (:content @captured))))))

  (describe "active-tools (via process-user-input!)"

    (it "uses tool dispatch path when tools are registered"
      (let [key-str       "agent:main:cli:direct:grover-tools"
             _             (storage/create-session! test-dir key-str)
             _             (tool-registry/register! {:name "echo" :description "Echo" :handler (fn [args] (:msg args))})
             tools-called  (atom false)
             stream-called (atom false)]
        (with-redefs [single-turn/check-compaction!        (fn [& _] nil)
                      dispatch/dispatch-chat-with-tools (fn [_ _ _ _]
                                                    (reset! tools-called true)
                                                    {:response {:message {:role "assistant" :content "done"}}})
                      single-turn/print-streaming-response  (fn [& _]
                                                       (reset! stream-called true)
                                                       {:content  "done"
                                                        :response {:message {:role "assistant" :content "done"}}})]
          (with-out-str
            (@#'single-turn/process-user-input! test-dir key-str "hi"
                                        {:model "test-model"
                                         :soul "You are helpful."
                                         :provider "grover"
                                         :provider-config {}
                                         :context-window 32768})))
        (should= true @tools-called)
        (should= false @stream-called))))

  (describe "dispatch-chat-with-tools"

    (it "calls the provider chat-with-tools and returns result"
      (with-redefs [ollama/chat-with-tools (fn [_ _ _]
                                             {:response   {:message {:role "assistant" :content "done"}}
                                              :tool-calls []
                                              :model      "echo"})]
        (let [tool-fn (fn [_ _] "tool result")
              result  (dispatch/dispatch-chat-with-tools "ollama" {} {:model "echo" :messages []} tool-fn)]
          (should-not (:error result))))))

  (describe "stream-and-handle-tools!"

    (it "calls print-streaming-response and returns its result"
      (with-redefs [single-turn/print-streaming-response (fn [_ _ _]
                                                   {:content "hello" :response {:message {:content "hello"}}})]
        (let [result (@#'single-turn/stream-and-handle-tools! "ollama" {} {:messages []} nil)]
          (should= "hello" (:content result)))))

    (it "loops when final chunk has tool_calls and recording-tool-fn is provided"
      (let [call-count  (atom 0)
            tool-called (atom nil)]
        (with-redefs [single-turn/print-streaming-response (fn [_ _ _]
                                                     (if (= 1 (swap! call-count inc))
                                                       {:content  ""
                                                        :response {:message {:tool_calls [{:function {:name "echo" :arguments {:msg "hi"}}}]}}}
                                                       {:content "result" :response {:message {:content "result"}}}))]
          (let [recording-fn (fn [name args]
                               (reset! tool-called {:name name :args args})
                               "echo result")
                result (@#'single-turn/stream-and-handle-tools! "ollama" {} {:messages []} recording-fn)]
            (should= 2 @call-count)
            (should= "echo" (:name @tool-called))
            (should= "result" (:content result))))))

    (it "does not loop when recording-tool-fn is nil even if tool_calls present"
      (let [call-count (atom 0)]
        (with-redefs [single-turn/print-streaming-response (fn [& _]
                                                     (swap! call-count inc)
                                                     {:content  ""
                                                      :response {:message {:tool_calls [{:function {:name "echo" :arguments {}}}]}}})]
          (@#'single-turn/stream-and-handle-tools! "ollama" {} {:messages []} nil)
          (should= 1 @call-count))))

    (it "returns error immediately without executing tools"
      (let [tool-called (atom false)]
        (with-redefs [single-turn/print-streaming-response (fn [& _]
                                                     {:error :timeout})]
          (let [result (@#'single-turn/stream-and-handle-tools! "ollama" {} {:messages []}
                                                         (fn [_ _] (reset! tool-called true) "result"))]
            (should= :timeout (:error result))
            (should= false @tool-called)))))

    (it "uses dispatch-chat-with-tools when tools are present in request"
      (let [stream-called (atom false)
            tools-called  (atom false)
            result        (atom nil)]
        (with-redefs [single-turn/print-streaming-response (fn [& _]
                                                     (reset! stream-called true)
                                                     {:content "unexpected"})
                      dispatch/dispatch-chat-with-tools (fn [_ _ _ tool-fn]
                                                    (reset! tools-called true)
                                                    (tool-fn "echo" {:msg "hi"})
                                                    {:response {:message {:role "assistant" :content "done"}}})]
          (with-out-str
            (reset! result (@#'single-turn/stream-and-handle-tools! "ollama" {} {:messages [] :tools [{:name "echo"}]}
                                                         (fn [_ _] "ok")))))
        (should= true @tools-called)
        (should= false @stream-called)
        (should= "done" (:content @result)))))

  (describe "run-tool-calls!"

    (it "stores tool calls and results in the transcript"
      (let [key-str "agent:main:cli:direct:tooltest"
            _       (storage/create-session! test-dir key-str)
            tool-results [[{:id "tc-1" :name "echo" :type "toolCall" :arguments {:msg "hi"}}
                           "echo result"]]]
        (single-turn/run-tool-calls! test-dir key-str tool-results)
        (let [transcript (storage/get-transcript test-dir key-str)
              messages   (filter #(= "message" (:type %)) transcript)]
          (should= 2 (count messages))
          (should= "assistant"  (get-in (first messages) [:message :role]))
          (should= "toolResult" (get-in (second messages) [:message :role])))))

    (it "marks tool results as errors when tool-fn returns an error string"
      (let [key-str "agent:main:cli:direct:toolerr"
            _       (storage/create-session! test-dir key-str)
            tool-results [[{:id "tc-1" :name "boom" :type "toolCall" :arguments {}}
                           "Error: something went wrong"]]]
        (single-turn/run-tool-calls! test-dir key-str tool-results)
        (let [transcript (storage/get-transcript test-dir key-str)
              tool-result (second (filter #(= "message" (:type %)) transcript))]
          (should= true (get-in tool-result [:message :isError])))))

  (describe "process-user-input!"

    (it "includes tools in the tool-dispatch request when tools are available"
      (let [key-str          "agent:main:cli:direct:tool-user"
             _                (storage/create-session! test-dir key-str)
             captured-request (atom nil)]
        (with-redefs [ctx/should-compact?            (constantly false)
                      tool-registry/tool-definitions  (fn [] [{:name "read" :description "Read a file" :parameters {}}])
                      tool-registry/tool-fn           (fn [] (fn [_ _] "README"))
                      dispatch/dispatch-chat-with-tools    (fn [_ _ request _]
                                                        (reset! captured-request request)
                                                        {:response {:message {:role "assistant" :content "summary"}}})]
          (with-out-str
            (@#'single-turn/process-user-input! test-dir key-str "summarize the readme"
                                        {:model "qwen"
                                         :soul "You are helpful."
                                         :provider "ollama"
                                         :provider-config {}
                                         :context-window 32768})))
        (should= 1 (count (:tools @captured-request)))))

    (it "preserves the triggering user message after compaction and completes chat"
      (let [key-str "agent:main:cli:direct:compact-user"
            _       (storage/create-session! test-dir key-str)
            _       (storage/append-message! test-dir key-str {:role "user" :content "Please summarize our work"})]
        (with-redefs [ctx/should-compact?        (constantly true)
                      ctx/compact!               (fn [sdir compact-key _]
                                                   (storage/append-compaction! sdir compact-key
                                                                             {:summary "Summary of prior chat"
                                                                              :firstKeptEntryId "kept-id"
                                                                              :tokensBefore 95}))
                      tool-registry/tool-definitions (constantly nil)
                      single-turn/print-streaming-response (fn [_ _ request]
                                                     {:content  "README summary"
                                                      :response {:message {:role "assistant" :content "README summary"}
                                                                 :usage   {:inputTokens 10 :outputTokens 5}
                                                                 :model   (:model request)}})]
          (with-out-str
            (@#'single-turn/process-user-input! test-dir key-str "Can you summarize README.md?"
                                        {:model "test-model"
                                         :soul "You are Isaac."
                                         :provider "grover"
                                         :provider-config {}
                                         :context-window 100})))
        (let [transcript (storage/get-transcript test-dir key-str)]
          (should= "compaction" (:type (nth transcript 2)))
          (should= "user" (get-in (nth transcript 3) [:message :role]))
          (should= [{:type "text" :text "Can you summarize README.md?"}]
                   (get-in (nth transcript 3) [:message :content]))
          (should= "assistant" (get-in (nth transcript 4) [:message :role]))
          (should= "README summary" (get-in (nth transcript 4) [:message :content]))))))

    (it "returns error result when LLM call fails"
      (let [key-str "agent:main:cli:direct:err-return"
            _       (storage/create-session! test-dir key-str)
            result  (atom nil)]
        (with-redefs [ctx/should-compact?          (constantly false)
                      tool-registry/tool-definitions (constantly nil)
                      single-turn/print-streaming-response  (fn [& _] {:error :connection-refused :message "refused"})]
          (with-out-str
            (reset! result (@#'single-turn/process-user-input! test-dir key-str "hello"
                                                        {:model "test" :soul "." :provider "ollama"
                                                         :provider-config {} :context-window 32768}))))
        (should= :connection-refused (:error @result))))

    (it "prints error to stdout when LLM call fails"
      (let [key-str "agent:main:cli:direct:err-print"
            _       (storage/create-session! test-dir key-str)
            output  (with-out-str
                      (with-redefs [ctx/should-compact?          (constantly false)
                                    tool-registry/tool-definitions (constantly nil)
                                    single-turn/print-streaming-response  (fn [& _] {:error :connection-refused
                                                                              :message "Connection refused"})]
                        (@#'chat-loop/maybe-process-input! test-dir key-str "hello"
                                                     {:model "test" :soul "." :provider "ollama"
                                                      :provider-config {} :context-window 32768})))]
        (should-contain "Error: Connection refused" output)))

    (it "prints [tool call: name] to stdout when a tool is called"
      (let [key-str    "agent:main:cli:direct:tool-status-print"
             _          (storage/create-session! test-dir key-str)
             output     (atom nil)]
        (with-redefs [ctx/should-compact?           (constantly false)
                      tool-registry/tool-definitions (fn [] [{:name "read_file" :description "Read" :parameters {}}])
                      tool-registry/tool-fn          (fn [] (fn [_ _] "contents"))
                      dispatch/dispatch-chat-with-tools   (fn [_ _ _ tool-fn]
                                                       (tool-fn "read_file" {:path "README.md"})
                                                       {:response {:message {:role "assistant" :content "done"}}})]
          (reset! output (with-out-str
                           (@#'single-turn/process-user-input! test-dir key-str "read it"
                                                        {:model "llama3" :soul "." :provider "ollama"
                                                         :provider-config {} :context-window 32768}))))
        (should-contain "[tool call: read_file]" @output)))

    (it "prints response content to stdout after tool calls complete"
      (let [key-str    "agent:main:cli:direct:tool-content-print"
             _          (storage/create-session! test-dir key-str)
             output     (atom nil)]
        (with-redefs [ctx/should-compact?           (constantly false)
                      tool-registry/tool-definitions (fn [] [{:name "read_file" :description "Read" :parameters {}}])
                      tool-registry/tool-fn          (fn [] (fn [_ _] "contents"))
                      dispatch/dispatch-chat-with-tools   (fn [_ _ _ tool-fn]
                                                       (tool-fn "read_file" {})
                                                       {:response {:message {:role "assistant" :content "The file says hello"}}})]
          (reset! output (with-out-str
                           (@#'single-turn/process-user-input! test-dir key-str "read it"
                                                        {:model "llama3" :soul "." :provider "ollama"
                                                         :provider-config {} :context-window 32768}))))
        (should-contain "The file says hello" @output)))

  ))
