(ns isaac.cli.chat-spec
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [isaac.cli.chat :as sut]
    [isaac.comm :as comm]
    [isaac.llm.anthropic :as anthropic]
    [isaac.cli.chat.toad :as toad]
    [isaac.llm.claude-sdk :as claude-sdk]
    [isaac.llm.ollama :as ollama]
    [isaac.llm.openai-compat :as openai-compat]
    [isaac.logger :as log]
    [isaac.drive.dispatch :as dispatch]
    [isaac.session.logging :as logging]
    [isaac.drive.turn :as single-turn]
    [isaac.context.manager :as ctx]
    [isaac.session.storage :as storage]
    [isaac.spec-helper :as helper]
    [isaac.tool.registry :as tool-registry]
    [isaac.util.shell :as shell]
    [isaac.fs :as fs]
    [speclj.core :refer :all]))

(def test-dir "/test/chat")

(defn- clean-dir! [path]
  (let [dir (io/file path)]
    (when (.exists dir)
      (doseq [f (reverse (file-seq dir))]
        (.delete f)))))

(defn- write-config! [home data]
  (let [path (str home "/.isaac/config/isaac.edn")]
    (fs/mkdirs (fs/parent path))
    (fs/spit path (pr-str data))))

(describe "CLI Chat"

  (before-all (clean-dir! test-dir))
  (after (do
           (clean-dir! test-dir)
           (single-turn/clear-async-compactions!)))
  (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

  (describe "run"

    (it "fails clearly when no config exists"
      (let [err (java.io.StringWriter.)]
        (binding [*err* err]
          (with-out-str
            (should= 1 (sut/run {:home "/test/chat-no-config" :dry-run true}))))
        (should-contain "no config found" (str err))
        (should-contain "/test/chat-no-config/.isaac/config/isaac.edn" (str err))))

    (it "launches Toad by default"
      (let [captured (atom nil)]
        (write-config! test-dir {})
        (with-redefs [shell/cmd-available? (constantly true)
                      toad/spawn-toad!     (fn [opts]
                                              (reset! captured opts)
                                              0)]
          (should= 0 (sut/run {:home test-dir}))
          (should= {:home test-dir} @captured))))

    (it "prints the dry-run command without requiring --toad"
      (write-config! test-dir {})
      (with-redefs [shell/cmd-available? (constantly true)]
        (let [output (with-out-str (should= 0 (sut/run {:home test-dir :dry-run true :resume true})))]
          (should-contain "toad" output)
          (should-contain "isaac acp --resume" output)))))

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

    (it "preserves tool call history with type:function for openai provider"
      (let [result (single-turn/build-chat-request "openai" {:api "openai-compatible"}
                     {:model      "gpt-5.4"
                      :soul       "You are helpful."
                      :transcript [{:type "message" :message {:role "user" :content "read the fridge"}}
                                   {:type "message" :message {:role "assistant"
                                                              :content [{:type "toolCall"
                                                                         :id "call_123"
                                                                         :name "read"
                                                                         :arguments {:filePath "fridge.txt"}}]}}
                                   {:type "message" :message {:role "toolResult"
                                                              :id "call_123"
                                                              :content "1 sad lemon, Hieronymus's emergency lettuce"}}
                                   {:type "message" :message {:role "assistant" :content "The fridge has a sad lemon and forbidden lettuce."}}]})
            msgs (:messages result)
            tool-msg (first (filter #(contains? % :tool_calls) msgs))
            tool-result-msg (first (filter #(= "tool" (:role %)) msgs))]
        (should-not-be-nil tool-msg)
        (should= "function" (get-in tool-msg [:tool_calls 0 :type]))
        (should= "read" (get-in tool-msg [:tool_calls 0 :function :name]))
        (should-not-be-nil tool-result-msg)
        (should= "call_123" (:tool_call_id tool-result-msg))))

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

    (it "resolves the ollama api explicitly"
      (should= "ollama" (@#'dispatch/resolve-api "ollama" {})))

    (it "marks tool results as errors when the result starts with Error"
      (let [messages (atom [])]
        (with-redefs [storage/append-message! (fn [_ _ message] (swap! messages conj message))]
          (single-turn/run-tool-calls! test-dir "agent:main:cli:direct:toolerr"
                               [[{:id "tc-1" :name "boom" :type "toolCall" :arguments {}}
                                 "Error: something went wrong"]])
          (should= true (:isError (second @messages))))))

    ))

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

    (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))
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
            last-entry (last transcript)]
        (should= "error" (:type last-entry))
        (should= ":connection-refused" (:error last-entry))
        (should= "refused" (:content last-entry))
        (should= "qwen:7b" (:model last-entry))
        (should= "ollama" (:provider last-entry))))

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
        (with-redefs [storage/get-session   (fn [_ key-str]
                                              (when (= key-str "agent:main:cli:direct:target")
                                                {:key "agent:main:cli:direct:target" :context-window 2}))
                      ctx/should-compact?  (fn [entry _]
                                             (reset! checked-entry entry)
                                             false)]
          (single-turn/check-compaction! test-dir "agent:main:cli:direct:target"
                                 {:model "m" :soul "s" :context-window 32768
                                  :provider "ollama" :provider-config {}})
          (should= "agent:main:cli:direct:target" (:key @checked-entry)))))

    (it "logs :session/compaction-check at debug with session, provider, model, totalTokens, context-window"
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
          (should= 100 (:context-window entry)))))

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
          (should= 100 (:context-window entry)))))

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

    (it "notifies the channel with 'compacting...' when compaction triggers"
      (let [key-str       "agent:main:cli:direct:channelatom"
            _             (storage/create-session! test-dir key-str)
            chunks        (atom [])
            mock-channel  (reify comm/Comm
                            (on-turn-start [_ _ _] nil)
                            (on-text-chunk [_ _ text] (swap! chunks conj text))
                            (on-tool-call [_ _ _] nil)
                            (on-tool-result [_ _ _ _] nil)
                            (on-turn-end [_ _ _] nil)
                            (on-error [_ _ _] nil))]
        (with-redefs [ctx/should-compact? (constantly true)
                      ctx/compact!        (fn [& _] nil)]
          (single-turn/check-compaction! test-dir key-str
                                 {:model "m" :soul "s" :context-window 100
                                  :provider "grover" :provider-config {}
                                  :channel mock-channel}))
        (should= ["compacting..."] @chunks)))

    (it "does not notify the channel when compaction does not trigger"
      (let [key-str       "agent:main:cli:direct:nochunk"
            _             (storage/create-session! test-dir key-str)
            chunks        (atom [])
            mock-channel  (reify comm/Comm
                            (on-turn-start [_ _ _] nil)
                            (on-text-chunk [_ _ text] (swap! chunks conj text))
                            (on-tool-call [_ _ _] nil)
                            (on-tool-result [_ _ _ _] nil)
                            (on-turn-end [_ _ _] nil)
                            (on-error [_ _ _] nil))]
        (with-redefs [ctx/should-compact? (constantly false)]
           (single-turn/check-compaction! test-dir key-str
                                  {:model "m" :soul "s" :context-window 100
                                   :provider "grover" :provider-config {}
                                   :channel mock-channel}))
        (should= [] @chunks)))

    (it "starts async compaction in the background when enabled"
      (let [key-str     "agent:main:cli:direct:asyncstart"
            _           (storage/create-session! test-dir key-str)
            _           (storage/update-session! test-dir key-str {:compaction {:strategy :slinky :threshold 80 :tail 40 :async? true}})
            entered?    (promise)
            release!    (promise)
            completed?  (atom false)]
        (with-redefs [ctx/should-compact? (constantly true)
                      ctx/compact!        (fn [& _]
                                            (deliver entered? true)
                                            @release!
                                            (reset! completed? true)
                                            {:type "compaction"})]
          (should-not= ::pending
                       (deref (future
                                (single-turn/check-compaction! test-dir key-str
                                                               {:model "m" :soul "s" :context-window 100
                                                                :provider "grover" :provider-config {}}))
                              100
                              ::pending))
          (should= true (deref entered? 100 false))
          (should (single-turn/async-compaction-in-flight? key-str))
          (deliver release! true)
          (single-turn/await-async-compaction! key-str)
          (should= true @completed?)))

    (it "skips starting a second async compaction while one is in flight"
      (let [key-str    "agent:main:cli:direct:asyncskip"
            _          (storage/create-session! test-dir key-str)
            _          (storage/update-session! test-dir key-str {:compaction {:strategy :slinky :threshold 80 :tail 40 :async? true}})
            attempts   (atom 0)
            entered?   (promise)
            release!   (promise)]
        (with-redefs [ctx/should-compact? (constantly true)
                      ctx/compact!        (fn [& _]
                                            (swap! attempts inc)
                                            (deliver entered? true)
                                            @release!
                                            {:type "compaction"})]
          (single-turn/check-compaction! test-dir key-str
                                         {:model "m" :soul "s" :context-window 100
                                          :provider "grover" :provider-config {}})
          (should= true (deref entered? 100 false))
          (single-turn/check-compaction! test-dir key-str
                                         {:model "m" :soul "s" :context-window 100
                                          :provider "grover" :provider-config {}})
          (should= 1 @attempts)
          (deliver release! true)
          (single-turn/await-async-compaction! key-str))))

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

    (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

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
                                         :context-window 32768
                                         :crew-members {"main" {:tools {:allow [:echo]}}}})))
        (should= true @tools-called)
        (should= false @stream-called)))

    (it "filters prompt tools to the crew member allow list"
      (let [key-str          "agent:main:cli:direct:allow-tools"
            _                (storage/create-session! test-dir key-str)
            captured-request (atom nil)]
        (with-redefs [single-turn/check-compaction!         (fn [& _] nil)
                      tool-registry/tool-definitions        (fn
                                                               ([] [{:name "read" :description "Read" :parameters {}}
                                                                    {:name "write" :description "Write" :parameters {}}
                                                                    {:name "exec" :description "Exec" :parameters {}}])
                                                               ([allowed]
                                                                (->> [{:name "read" :description "Read" :parameters {}}
                                                                      {:name "write" :description "Write" :parameters {}}
                                                                      {:name "exec" :description "Exec" :parameters {}}]
                                                                     (filter #(contains? allowed (:name %)))
                                                                     vec)))
                      tool-registry/tool-fn                 (fn
                                                               ([] (fn [_ _] nil))
                                                               ([_] (fn [_ _] nil)))
                      dispatch/dispatch-chat-with-tools     (fn [_ _ request _]
                                                              (reset! captured-request request)
                                                              {:response {:message {:role "assistant" :content "summary"}}})]
          (with-out-str
            (@#'single-turn/process-user-input! test-dir key-str "summarize the readme"
                                                {:model "qwen"
                                                 :soul "You are helpful."
                                                 :provider "ollama"
                                                 :provider-config {}
                                                 :context-window 32768
                                                 :crew-members {"main" {:model "local" :tools {:allow [:read :write]}}}})))
        (should= ["read" "write"] (mapv #(or (:name %) (get-in % [:function :name])) (:tools @captured-request)))))

    (it "omits tools when the crew member has an empty tools allow list"
      (let [key-str       "agent:main:cli:direct:no-tools"
            _             (storage/create-session! test-dir key-str)
            tools-called  (atom false)
            stream-called (atom false)]
        (with-redefs [single-turn/check-compaction!         (fn [& _] nil)
                      dispatch/dispatch-chat-with-tools      (fn [& _]
                                                              (reset! tools-called true)
                                                              {:response {:message {:role "assistant" :content "done"}}})
                      single-turn/print-streaming-response   (fn [& _]
                                                              (reset! stream-called true)
                                                              {:content  "done"
                                                               :response {:message {:role "assistant" :content "done"}}})]
          (with-out-str
            (@#'single-turn/process-user-input! test-dir key-str "hi"
                                                {:model "test-model"
                                                 :soul "You are helpful."
                                                 :provider "grover"
                                                 :provider-config {}
                                                 :context-window 32768
                                                 :crew-members {"main" {:model "local" :tools {:allow []}}}})))
        (should= false @tools-called)
        (should= true @stream-called))))

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

    (it "formats live tool loop messages for openai-compatible providers"
      (let [requests (atom [])]
        (with-redefs [single-turn/print-streaming-response (fn [_ _ req]
                                                             (swap! requests conj req)
                                                             (if (= 1 (count @requests))
                                                               {:content  ""
                                                                :response {:message {:tool_calls [{:id "call_123"
                                                                                                   :function {:name "echo"
                                                                                                              :arguments {:msg "hi"}}}]}}}
                                                               {:content "done" :response {:message {:content "done"}}}))]
          (let [result       (@#'single-turn/stream-and-handle-tools! "openai" {:api "openai-compatible"}
                                                                      {:messages [{:role "user" :content "say hi"}]}
                                                                      (fn [_ _] "echo result"))
                loop-request (:loop-request result)
                assistant    (first (filter #(contains? % :tool_calls) (:messages loop-request)))
                tool-result  (first (filter #(= "tool" (:role %)) (:messages loop-request)))]
            (should-not-be-nil loop-request)
            (should= "function" (get-in assistant [:tool_calls 0 :type]))
            (should= "echo" (get-in assistant [:tool_calls 0 :function :name]))
            (should= (json/generate-string {:msg "hi"}) (get-in assistant [:tool_calls 0 :function :arguments]))
            (should= "call_123" (:tool_call_id tool-result))
            (should= "echo result" (:content tool-result))))))

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

  (describe "process-user-input!"

    (it "sends a cancelled tool update when a tool call is interrupted"
      (let [real-dir  (str (System/getProperty "user.dir") "/target/test-chat-cancel")
            key-str   "agent:main:cli:direct:cancel-tool"
            _         (storage/create-session! real-dir key-str)
            started*  (promise)
            release*  (promise)
            events    (atom [])
            ch        (reify comm/Comm
                        (on-turn-start [_ _ _] nil)
                        (on-text-chunk [_ _ _] nil)
                        (on-tool-call [_ _ tool-call]
                          (swap! events conj [:tool-call (:id tool-call)]))
                        (on-tool-cancel [_ _ tool-call]
                          (swap! events conj [:tool-cancel (:id tool-call)]))
                        (on-tool-result [_ _ tool-call _]
                          (swap! events conj [:tool-result (:id tool-call)]))
                        (on-turn-end [_ _ _] nil)
                        (on-error [_ _ _] nil))]
        (tool-registry/register! {:name        "sleepy"
                                  :description "waits until cancelled"
                                  :parameters  {}
                                  :handler     (fn [_]
                                                 (deliver started* :started)
                                                 @release*
                                                 {:error :cancelled})})
        (with-redefs [single-turn/stream-and-handle-tools! (fn [_channel _session-key _provider _provider-config _request recording-tool-fn]
                                                             (recording-tool-fn "sleepy" {:command "sleep 30"}))]
          (let [turn (future
                       (single-turn/process-user-input! real-dir key-str "run it"
                                                       {:channel         ch
                                                        :model           "echo"
                                                        :soul            "You are helpful."
                                                        :provider        "grover"
                                                        :provider-config {}
                                                        :context-window  32768
                                                        :crew-members    {"main" {:tools {:allow [:sleepy]}}}}))]
            @started*
            (isaac.session.bridge/cancel! key-str)
            (deliver release* :released)
            (let [result @turn]
              (should= "cancelled" (:stopReason result))
              (should= 2 (count @events))
              (should= :tool-call (ffirst @events))
              (should= :tool-cancel (ffirst (rest @events)))
              (should= (second (first @events)) (second (second @events)))))))))

  (describe "run-tool-calls!"

    (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

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
                      tool-registry/tool-definitions  (fn
                                                         ([] [{:name "read" :description "Read a file" :parameters {}}])
                                                         ([_] [{:name "read" :description "Read a file" :parameters {}}]))
                      tool-registry/tool-fn           (fn
                                                         ([] (fn [_ _] "README"))
                                                         ([_] (fn [_ _] "README")))
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

    (it "passes the session state directory through provider config"
      (let [key-str              "agent:main:cli:direct:state-dir-provider"
            _                    (storage/create-session! test-dir key-str)
            captured-provider-cfg (atom nil)]
        (with-redefs [ctx/should-compact?                 (constantly false)
                      tool-registry/tool-definitions      (constantly nil)
                      single-turn/stream-and-handle-tools! (fn [_ _ _ provider-config _ _]
                                                             (reset! captured-provider-cfg provider-config)
                                                             {:content  "Hello"
                                                              :response {:message {:role "assistant" :content "Hello"}
                                                                         :usage   {:inputTokens 2 :outputTokens 1}
                                                                         :model   "echo"}})]
          (with-out-str
            (@#'single-turn/process-user-input! test-dir key-str "hello"
                                        {:model "echo"
                                         :soul "You are Isaac."
                                         :provider "openai-codex"
                                         :provider-config {:auth "oauth-device" :name "openai-chatgpt"}
                                         :context-window 32768})))
        (should= test-dir (:state-dir @captured-provider-cfg))))

    (it "rejects a turn when the session crew is unknown"
      (let [key-str       "agent:main:cli:direct:unknown-crew"
            _             (storage/create-session! test-dir key-str {:crew "marvin"})
            result        (atom nil)
            output        (atom nil)
            stream-called (atom false)]
        (log/capture-logs
          (with-redefs [ctx/should-compact?                 (constantly false)
                        single-turn/stream-and-handle-tools! (fn [& _]
                                                               (reset! stream-called true)
                                                               {:content "should not happen"})]
            (reset! output (with-out-str
                             (reset! result (@#'single-turn/process-user-input! test-dir key-str "hello"
                                                             {:model "echo"
                                                              :soul "You are Isaac."
                                                              :provider "grover"
                                                              :provider-config {}
                                                              :context-window 32768
                                                              :crew-members {"main" {:model "grover" :soul "You are Isaac."}}
                                                              :models {"grover" {:model "echo" :provider "grover" :context-window 32768}}}))))))
        (should= :unknown-crew (:error @result))
        (should-contain "unknown crew: marvin" @output)
        (should-contain "use /crew <name> to switch, or add marvin to config" @output)
        (should-not @stream-called)
        (should= [] (filter #(= "message" (:type %)) (storage/get-transcript test-dir key-str)))
        (let [entry (last @log/captured-logs)]
          (should= :turn/rejected (:event entry))
          (should= key-str (:session entry))
          (should= "marvin" (:crew entry))
          (should= :unknown-crew (:reason entry)))))

    (it "logs accepted turns with the session crew"
      (let [key-str "agent:main:cli:direct:accepted-turn"
            _       (storage/create-session! test-dir key-str {:crew "main"})]
        (log/capture-logs
          (with-redefs [ctx/should-compact?                 (constantly false)
                        tool-registry/tool-definitions      (constantly nil)
                        single-turn/stream-and-handle-tools! (fn [& _]
                                                               {:content  "Hello"
                                                                :response {:message {:role "assistant" :content "Hello"}
                                                                           :usage   {:inputTokens 2 :outputTokens 1}
                                                                           :model   "echo"}})]
            (with-out-str
              (@#'single-turn/process-user-input! test-dir key-str "hello"
                                          {:model "echo"
                                           :soul "You are Isaac."
                                           :provider "grover"
                                           :provider-config {}
                                           :context-window 32768
                                           :crew-members {"main" {:model "grover" :soul "You are Isaac."}}
                                           :models {"grover" {:model "echo" :provider "grover" :context-window 32768}}})))
          (should (some #(and (= :turn/accepted (:event %))
                              (= key-str (:session %))
                              (= "main" (:crew %)))
                        @log/captured-logs)))))

    (it "prints [tool call: name] to stdout when a tool is called"
      (let [key-str    "agent:main:cli:direct:tool-status-print"
             _          (storage/create-session! test-dir key-str)
             output     (atom nil)]
        (with-redefs [ctx/should-compact?           (constantly false)
                      tool-registry/tool-definitions (fn
                                                       ([] [{:name "read_file" :description "Read" :parameters {}}])
                                                       ([_] [{:name "read_file" :description "Read" :parameters {}}]))
                      tool-registry/tool-fn          (fn
                                                        ([] (fn [_ _] "contents"))
                                                        ([_] (fn [_ _] "contents")))
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
                      tool-registry/tool-definitions (fn
                                                       ([] [{:name "read_file" :description "Read" :parameters {}}])
                                                       ([_] [{:name "read_file" :description "Read" :parameters {}}]))
                      tool-registry/tool-fn          (fn
                                                        ([] (fn [_ _] "contents"))
                                                        ([_] (fn [_ _] "contents")))
                      dispatch/dispatch-chat-with-tools   (fn [_ _ _ tool-fn]
                                                       (tool-fn "read_file" {})
                                                       {:response {:message {:role "assistant" :content "The file says hello"}}})]
          (reset! output (with-out-str
                           (@#'single-turn/process-user-input! test-dir key-str "read it"
                                                        {:model "llama3" :soul "." :provider "ollama"
                                                         :provider-config {} :context-window 32768}))))
        (should-contain "The file says hello" @output)))

  ))
