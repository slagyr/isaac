(ns isaac.cli.chat-spec
  (:require
    [clojure.java.io :as io]
    [isaac.llm.anthropic :as anthropic]
    [isaac.llm.claude-sdk :as claude-sdk]
    [isaac.llm.ollama :as ollama]
    [isaac.llm.openai-compat :as openai-compat]
    [isaac.logger :as log]
    [isaac.cli.chat :as sut]
    [isaac.config.resolution :as config]
    [isaac.context.manager :as ctx]
    [isaac.session.storage :as storage]
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

    (it "uses provider base-url and context window for provider/model refs"
      (let [cfg {:agents {:defaults {:model "openai/gpt-5"}}
                 :models {:providers [{:name          "openai"
                                       :baseUrl       "https://api.openai.com/v1"
                                       :contextWindow 128000
                                       :api           "openai-compatible"}]}}
            ctx (sut/prepare {:agent "main"}
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

    (it "reports only message entries when resuming a session"
      (let [cfg     {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}
                     :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            key-str "agent:main:cli:direct:countuser"
            _       (storage/create-session! test-dir key-str)
            _       (storage/append-message! test-dir key-str {:role "user" :content "hello"})
            _       (storage/append-compaction! test-dir key-str {:summary "short" :tokensBefore 10})
            output  (with-out-str
                      (sut/prepare {:agent "main" :resume true}
                                   {:cfg  cfg
                                    :sdir test-dir}))]
        (should-contain "1 messages" output)))

    (it "falls back to default context-window"
      (let [cfg {:agents {:defaults {:model "ollama/qwen3-coder:30b"}}
                 :models {:providers [{:name "ollama" :baseUrl "http://localhost:11434"}]}}
            ctx (sut/prepare {:agent "main"}
                             {:cfg  cfg
                               :sdir test-dir})]
        (should= 32768 (:context-window ctx))))

    (it "falls back to default ollama config for unqualified model refs"
      (let [cfg {:agents {:defaults {:model "qwen3-coder:30b"}}
                 :models {:providers [{:name "ollama"}]}}
            ctx (sut/prepare {:agent "main"}
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

  (describe "private helpers"

    (it "parses model refs when no alias is configured"
      (let [cfg    {:agents {:defaults {:model "openai/gpt-5"}}
                    :models {:providers [{:name "openai" :baseUrl "https://api.openai.com/v1"}]}}
            result (@#'sut/resolve-model-info cfg {} nil)]
        (should= "gpt-5" (:model result))
        (should= "openai" (:provider result))))

    (it "resolves the ollama api explicitly"
      (should= "ollama" (@#'sut/resolve-api "ollama" {})))

    (it "marks tool results as errors when the result starts with Error"
      (let [messages (atom [])]
        (with-redefs [storage/append-message! (fn [_ _ message] (swap! messages conj message))]
          (sut/run-tool-calls! test-dir "agent:main:cli:direct:toolerr"
                               [[{:id "tc-1" :name "boom" :type "toolCall" :arguments {}}
                                 "Error: something went wrong"]])
          (should= true (:isError (second @messages))))))

    (it "processes non-blank input and ignores blank input"
      (let [calls (atom [])]
        (with-redefs [sut/process-user-input! (fn [_ _ input _] (swap! calls conj input))]
          (@#'sut/maybe-process-input! test-dir "agent:main:cli:direct:test" "hello" {})
          (@#'sut/maybe-process-input! test-dir "agent:main:cli:direct:test" "   " {})
          (should= ["hello"] @calls)))))

  (describe "dispatch-chat"

    (it "dispatches claude-sdk requests and logs success"
      (let [events (atom [])]
        (with-redefs [claude-sdk/chat (fn [_] {:model "sonnet" :message {:role "assistant" :content "hi"}})
                      log/log*        (fn [level data _file _line]
                                         (swap! events conj (assoc data :level level)))]
          (let [result (sut/dispatch-chat "claude-sdk" {} {:model "m" :messages []})]
            (should= "sonnet" (:model result))
            (should= [:chat/request :chat/response] (mapv :event @events))))))

    (it "dispatches openai-compatible errors and logs them"
      (let [events (atom [])]
        (with-redefs [openai-compat/chat (fn [_ _] {:error :auth-failed :status 401})
                      log/log*          (fn [level data _file _line]
                                           (swap! events conj (assoc data :level level)))]
          (let [result (sut/dispatch-chat "openai" {:api "openai-compatible"} {:model "m" :messages []})]
            (should= :auth-failed (:error result))
            (should= [:chat/request :chat/error] (mapv :event @events)))))))

  (describe "dispatch-chat-stream"

    (it "dispatches ollama stream requests and logs success"
      (let [events (atom [])
            chunks (atom [])]
        (with-redefs [ollama/chat-stream (fn [_ on-chunk _]
                                           (on-chunk {:message {:content "hi"}})
                                           {:model "qwen" :message {:role "assistant" :content "hi"}})
                      log/log*          (fn [level data _file _line]
                                           (swap! events conj (assoc data :level level)))]
          (let [result (sut/dispatch-chat-stream "ollama" {} {:model "m" :messages []}
                                                 #(swap! chunks conj %))]
            (should= "qwen" (:model result))
            (should= 1 (count @chunks))
            (should= [:chat/stream-request :chat/stream-response] (mapv :event @events))))))

    (it "dispatches anthropic stream errors and logs them"
      (let [events (atom [])]
        (with-redefs [anthropic/chat-stream (fn [_ _ _] {:error :connection-refused})
                      log/log*             (fn [level data _file _line]
                                              (swap! events conj (assoc data :level level)))]
          (let [result (sut/dispatch-chat-stream "anthropic" {:api "anthropic-messages"} {:model "m" :messages []} identity)]
            (should= :connection-refused (:error result))
            (should= [:chat/stream-request :chat/stream-error] (mapv :event @events)))))))

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
        (should-contain "Error: API timeout" output)))

    (it "prints body error details when message is absent"
      (let [output (with-out-str
                     (sut/process-response! test-dir "agent:x:cli:direct:x"
                                            {:error  :api-error
                                             :status 400
                                             :body   {:error {:type "invalid_request_error"
                                                              :message "Bad request"}}}
                                            {:model "m" :provider "p"}))]
        (should-contain "invalid_request_error: Bad request" output)))

    (it "prints http status details when only status and body are available"
      (let [output (with-out-str
                     (sut/process-response! test-dir "agent:x:cli:direct:x"
                                            {:error  :api-error
                                             :status 503}
                                            {:model "m" :provider "p"}))]
        (should-contain "HTTP 503 api-error" output)))

    (it "logs :chat/response-failed at error with session and provider on error"
      (let [logged (atom [])]
        (with-redefs [log/log* (fn [level data _ _] (swap! logged conj {:level level :data data}))]
          (with-out-str
            (sut/process-response! test-dir "agent:x:cli:direct:x"
                                   {:error :connection-refused}
                                   {:model "m" :provider "ollama"})))
        (let [entry (first (filter #(= :chat/response-failed (get-in % [:data :event])) @logged))]
          (should-not-be-nil entry)
          (should= :error (:level entry))
          (should= "ollama" (get-in entry [:data :provider]))
          (should= "agent:x:cli:direct:x" (get-in entry [:data :session])))))

    (it "logs :chat/message-stored at debug with session and model on success"
      (let [key-str "agent:main:cli:direct:log-test"
            _       (storage/create-session! test-dir key-str)
            logged  (atom [])]
        (with-redefs [log/log* (fn [level data _ _] (swap! logged conj {:level level :data data}))]
          (sut/process-response! test-dir key-str
                                 {:content  "Hello!"
                                  :response {:model "grover" :usage {:inputTokens 10 :outputTokens 5}}}
                                 {:model "grover" :provider "grover"}))
        (let [entry (first (filter #(= :chat/message-stored (get-in % [:data :event])) @logged))]
          (should-not-be-nil entry)
          (should= :debug (:level entry))
          (should= key-str (get-in entry [:data :session]))
          (should= "grover" (get-in entry [:data :model])))))

  (describe "log-stream-completed!"

    (it "logs :chat/stream-completed at debug with session"
      (let [logged (atom [])]
        (with-redefs [log/log* (fn [level data _ _] (swap! logged conj {:level level :data data}))]
          (sut/log-stream-completed! "agent:x:cli:direct:x"))
        (let [entry (first @logged)]
          (should= :debug (:level entry))
          (should= :chat/stream-completed (get-in entry [:data :event]))
          (should= "agent:x:cli:direct:x" (get-in entry [:data :session]))))))

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

    (it "passes the matching session entry to compaction checks"
      (let [checked-entry (atom nil)]
        (with-redefs [storage/list-sessions (fn [_ _]
                                              [{:key "agent:main:cli:direct:other" :context-window 1}
                                               {:key "agent:main:cli:direct:target" :context-window 2}])
                      ctx/should-compact?  (fn [entry _]
                                             (reset! checked-entry entry)
                                             false)]
          (sut/check-compaction! test-dir "agent:main:cli:direct:target"
                                 {:model "m" :soul "s" :context-window 32768
                                  :provider "ollama" :provider-config {}})
          (should= "agent:main:cli:direct:target" (:key @checked-entry)))))

    (it "logs :context/compaction-check at debug with session, provider, model, totalTokens, contextWindow"
      (let [key-str "agent:main:cli:direct:checklog"
            _       (storage/create-session! test-dir key-str)
            _       (storage/update-tokens! test-dir key-str {:inputTokens 50 :outputTokens 0})
            logged  (atom [])]
        (with-redefs [log/log*            (fn [level data _ _] (swap! logged conj {:level level :data data}))
                      ctx/should-compact? (constantly false)]
          (sut/check-compaction! test-dir key-str
                                 {:model "echo" :soul "s" :context-window 100
                                  :provider "grover" :provider-config {}}))
        (let [entry (first (filter #(= :context/compaction-check (get-in % [:data :event])) @logged))]
          (should-not-be-nil entry)
          (should= :debug (:level entry))
          (should= key-str (get-in entry [:data :session]))
          (should= "grover" (get-in entry [:data :provider]))
          (should= "echo" (get-in entry [:data :model]))
          (should= 50 (get-in entry [:data :totalTokens]))
          (should= 100 (get-in entry [:data :contextWindow])))))

    (it "logs :context/compaction-started at info when compaction triggers"
      (let [key-str "agent:main:cli:direct:startlog"
            _       (storage/create-session! test-dir key-str)
            _       (storage/update-tokens! test-dir key-str {:inputTokens 50 :outputTokens 0})
            logged  (atom [])]
        (with-redefs [log/log*            (fn [level data _ _] (swap! logged conj {:level level :data data}))
                      ctx/should-compact? (constantly true)
                      ctx/compact!        (fn [& _] nil)]
          (with-out-str
            (sut/check-compaction! test-dir key-str
                                   {:model "echo" :soul "s" :context-window 100
                                    :provider "grover" :provider-config {}})))
        (let [entry (first (filter #(= :context/compaction-started (get-in % [:data :event])) @logged))]
          (should-not-be-nil entry)
          (should= :info (:level entry))
          (should= key-str (get-in entry [:data :session]))
          (should= "grover" (get-in entry [:data :provider]))
          (should= "echo" (get-in entry [:data :model]))
          (should= 50 (get-in entry [:data :totalTokens]))
          (should= 100 (get-in entry [:data :contextWindow])))))

    (it "does not log :context/compaction-started when under threshold"
      (let [key-str "agent:main:cli:direct:nolog"
            _       (storage/create-session! test-dir key-str)
            logged  (atom [])]
        (with-redefs [log/log*            (fn [level data _ _] (swap! logged conj {:level level :data data}))
                      ctx/should-compact? (constantly false)]
          (sut/check-compaction! test-dir key-str
                                 {:model "m" :soul "s" :context-window 100
                                  :provider "grover" :provider-config {}}))
        (let [entry (first (filter #(= :context/compaction-started (get-in % [:data :event])) @logged))]
          (should-be-nil entry))))

    (it "logs :context/compaction-failed at error when compact! returns an error"
      (let [key-str "agent:main:cli:direct:faillog"
            _       (storage/create-session! test-dir key-str)
            logged  (atom [])]
        (with-redefs [log/log*            (fn [level data _ _] (swap! logged conj {:level level :data data}))
                      ctx/should-compact? (constantly true)
                      ctx/compact!        (fn [& _] {:error :llm-error :message "context length exceeded"})]
          (with-out-str
            (sut/check-compaction! test-dir key-str
                                   {:model "m" :soul "s" :context-window 100
                                    :provider "grover" :provider-config {}})))
        (let [entry (first (filter #(= :context/compaction-failed (get-in % [:data :event])) @logged))]
          (should-not-be-nil entry)
          (should= :error (:level entry))
          (should= key-str (get-in entry [:data :session]))))))

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

    (it "prefers openai streamed delta content over fallback result content"
      (with-redefs [sut/dispatch-chat-stream (fn [_ _ _ on-chunk]
                                               (on-chunk {:choices [{:delta {:content "streamed"}}]})
                                               {:message {:role "assistant" :content "fallback"}})]
        (let [captured (atom nil)]
          (with-out-str
            (reset! captured (sut/print-streaming-response "openai" {} {})))
          (should= "streamed" (:content @captured)))))

    (it "uses result message content when no streaming content"
      (with-redefs [sut/dispatch-chat-stream (fn [_ _ _ _]
                                               {:message {:role "assistant" :content "fallback"}})]
        (let [captured (atom nil)]
          (with-out-str
            (reset! captured (sut/print-streaming-response "ollama" {} {})))
          (should= "fallback" (:content @captured)))))

    (it "keeps the done chunk as the final response"
      (with-redefs [sut/dispatch-chat-stream (fn [_ _ _ on-chunk]
                                               (on-chunk {:message {:content "Hello"}})
                                               (on-chunk {:done true :message {:content " world"} :status :finished})
                                               {:message {:role "assistant" :content "ignored"}})]
        (let [captured (atom nil)]
          (with-out-str
            (reset! captured (sut/print-streaming-response "ollama" {} {})))
          (should= :finished (get-in @captured [:response :status])))))

    (it "does not duplicate a final done chunk that repeats the full content"
      (with-redefs [sut/dispatch-chat-stream (fn [_ _ _ on-chunk]
                                               (on-chunk {:message {:content "README "}})
                                               (on-chunk {:done true :message {:content "README summary"}})
                                               {:message {:role "assistant" :content "README summary"}})]
        (let [captured (atom nil)]
          (with-out-str
            (reset! captured (sut/print-streaming-response "grover" {} {})))
          (should= "README summary" (:content @captured))))))

  (describe "dispatch-chat-with-tools"

    (it "calls the provider chat-with-tools and returns result"
      (with-redefs [ollama/chat-with-tools (fn [_ _ _]
                                             {:response   {:message {:role "assistant" :content "done"}}
                                              :tool-calls []
                                              :model      "echo"})]
        (let [tool-fn (fn [_ _] "tool result")
              result  (sut/dispatch-chat-with-tools "ollama" {} {:model "echo" :messages []} tool-fn)]
          (should-not (:error result))))))

  (describe "run-tool-calls!"

    (it "stores tool calls and results in the transcript"
      (let [key-str "agent:main:cli:direct:tooltest"
            _       (storage/create-session! test-dir key-str)
            tool-results [[{:id "tc-1" :name "echo" :type "toolCall" :arguments {:msg "hi"}}
                           "echo result"]]]
        (sut/run-tool-calls! test-dir key-str tool-results)
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
        (sut/run-tool-calls! test-dir key-str tool-results)
        (let [transcript (storage/get-transcript test-dir key-str)
              tool-result (second (filter #(= "message" (:type %)) transcript))]
          (should= true (get-in tool-result [:message :isError])))))

  (describe "process-user-input!"

    (it "uses tool-enabled chat when tools are available"
      (let [key-str    "agent:main:cli:direct:tool-user"
            _          (storage/create-session! test-dir key-str)
            dispatched (atom nil)]
        (with-redefs [ctx/should-compact?          (constantly false)
                      tool-registry/tool-definitions (fn [] [{:name "read" :description "Read a file" :parameters {}}])
                      tool-registry/tool-fn          (fn [] (fn [_ _] "README"))
                      sut/dispatch-chat-with-tools   (fn [_ _ request tool-fn]
                                                       (reset! dispatched {:request request :tool-result (tool-fn "read" {:filePath "README.md"})})
                                                       {:response     {:message {:role "assistant" :content "summary"}
                                                                       :usage   {:inputTokens 10 :outputTokens 5}}
                                                        :tool-calls   []
                                                        :token-counts {:inputTokens 10 :outputTokens 5}})
                      sut/print-streaming-response   (fn [& _] (throw (ex-info "should not stream" {})))]
          (with-out-str
            (@#'sut/process-user-input! test-dir key-str "summarize the readme"
                                        {:model "qwen"
                                         :soul "You are helpful."
                                         :provider "ollama"
                                         :provider-config {}
                                         :context-window 32768}))
           (should= "README" (:tool-result @dispatched))
           (should= 1 (count (:tools (:request @dispatched)))))))

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
                      sut/print-streaming-response (fn [_ _ request]
                                                     {:content  "README summary"
                                                      :response {:message {:role "assistant" :content "README summary"}
                                                                 :usage   {:inputTokens 10 :outputTokens 5}
                                                                 :model   (:model request)}})]
          (with-out-str
            (@#'sut/process-user-input! test-dir key-str "Can you summarize README.md?"
                                        {:model "test-model"
                                         :soul "You are Isaac."
                                         :provider "grover"
                                         :provider-config {}
                                         :context-window 100})))
        (let [transcript (storage/get-transcript test-dir key-str)]
          (should= "compaction" (:type (nth transcript 2)))
          (should= "user" (get-in (nth transcript 3) [:message :role]))
          (should= "Can you summarize README.md?" (get-in (nth transcript 3) [:message :content]))
          (should= "assistant" (get-in (nth transcript 4) [:message :role]))
          (should= "README summary" (get-in (nth transcript 4) [:message :content]))))))

  ))
