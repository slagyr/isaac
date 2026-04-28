(ns isaac.llm.openai-compat-spec
  (:require
    [babashka.http-client :as http]
    [cheshire.core :as json]
    [isaac.auth.store :as auth-store]
    [isaac.llm.http :as llm-http]
    [isaac.llm.openai-compat :as sut]
    [speclj.core :refer :all]))

(defn- mock-response [body]
  {:status 200 :body (json/generate-string body)})

(defn- responses-response [content & {:keys [model input-tokens output-tokens]
                                      :or   {model "gpt-5.4" input-tokens 10 output-tokens 5}}]
  (mock-response {:output [{:type    "message"
                            :role    "assistant"
                            :content [{:type "output_text" :text content}]}]
                  :model  model
                  :usage  {:input_tokens input-tokens :output_tokens output-tokens}}))

(defn- jwt-with-account-id [account-id]
  (let [payload (json/generate-string {"https://api.openai.com/auth" {"chatgpt_account_id" account-id}})
        enc     (.withoutPadding (java.util.Base64/getUrlEncoder))]
    (str "x." (.encodeToString enc (.getBytes payload "UTF-8")) ".y")))

(defn- chat-response [content & {:keys [model tool-calls prompt-tokens completion-tokens]
                                  :or   {model "gpt-5" prompt-tokens 10 completion-tokens 5}}]
  (mock-response {:choices [{:message (cond-> {:role "assistant" :content content}
                                        tool-calls (assoc :tool_calls tool-calls))}]
                  :model   model
                  :usage   {:prompt_tokens prompt-tokens :completion_tokens completion-tokens}}))

(def test-config {:apiKey "sk-test" :baseUrl "https://api.example.com/v1"})

(describe "OpenAI-Compatible Client"

  (describe "chat"

    (it "parses a text response from choices array"
      (with-redefs [http/post (fn [_ _] (chat-response "Hello!"))]
        (let [result (sut/chat {:model "gpt-5" :messages []} {:provider-config test-config})]
          (should= "Hello!" (get-in result [:message :content]))
          (should= "gpt-5" (:model result)))))

    (it "parses token usage"
      (with-redefs [http/post (fn [_ _] (chat-response "Hi" :prompt-tokens 42 :completion-tokens 18))]
        (let [result (sut/chat {:model "gpt-5" :messages []} {:provider-config test-config})]
          (should= 42 (:input-tokens (:usage result)))
          (should= 18 (:output-tokens (:usage result))))))

    (it "extracts tool calls with string arguments"
      (with-redefs [http/post (fn [_ _] (chat-response ""
                                           :tool-calls [{:id "tc1"
                                                         :function {:name      "read_file"
                                                                    :arguments "{\"path\":\"README\"}"}}]))]
        (let [result (sut/chat {:model "gpt-5" :messages []} {:provider-config test-config})]
          (should= 1 (count (:tool-calls result)))
          (should= "read_file" (:name (first (:tool-calls result))))
          (should= {:path "README"} (:arguments (first (:tool-calls result)))))))

    (it "extracts tool calls with map arguments"
      (with-redefs [http/post (fn [_ _] (chat-response ""
                                           :tool-calls [{:id "tc1"
                                                         :function {:name      "read_file"
                                                                    :arguments {:path "README"}}}]))]
        (let [result (sut/chat {:model "gpt-5" :messages []} {:provider-config test-config})]
          (should= {:path "README"} (:arguments (first (:tool-calls result)))))))

    (it "sets Authorization Bearer header"
      (let [captured-headers (atom nil)]
        (with-redefs [http/post (fn [_ opts] (reset! captured-headers (:headers opts)) (chat-response ""))]
          (sut/chat {:model "test" :messages []} {:provider-config test-config})
          (should= "Bearer sk-test" (get @captured-headers "Authorization")))))

    (it "uses OAuth access token when auth is oauth-device"
      (let [captured-headers (atom nil)
            oauth-config     {:baseUrl "https://api.openai.com/v1" :auth "oauth-device" :name "openai-chatgpt"}
            token            (jwt-with-account-id "acct-123")]
        (with-redefs [llm-http/post-sse!         (fn [_ headers _ _ process-event initial & _]
                                                   (reset! captured-headers headers)
                                                   (process-event {:type "response.completed"
                                                                   :response {:model "gpt-5.4"
                                                                              :usage {:input_tokens 10 :output_tokens 5}}}
                                                                  initial))
                      auth-store/load-tokens    (fn [_ _] {:type "oauth" :access token :expires (+ (System/currentTimeMillis) 60000)})
                      auth-store/token-expired? (fn [_] false)]
          (sut/chat {:model "test" :messages [{:role "user" :content "hi"}]} {:provider-config oauth-config})
          (should= (str "Bearer " token)
                   (get @captured-headers "Authorization")))))

    (it "uses chatgpt codex responses endpoint for oauth-device"
      (let [captured-url  (atom nil)
            captured-body (atom nil)
            oauth-config  {:baseUrl "https://api.openai.com/v1" :auth "oauth-device" :name "openai-chatgpt"}
            token         (jwt-with-account-id "acct-123")]
        (with-redefs [llm-http/post-sse!         (fn [url _ body _ process-event initial & _]
                                                   (reset! captured-url url)
                                                   (reset! captured-body body)
                                                   (process-event {:type "response.output_text.delta" :delta "Hello from Codex"}
                                                                  initial))
                      auth-store/load-tokens    (fn [_ _] {:type "oauth" :access token :expires (+ (System/currentTimeMillis) 60000)})
                      auth-store/token-expired? (fn [_] false)]
          (let [result (sut/chat {:model   "gpt-5.4"
                                  :system  "You are Codex."
                                  :messages [{:role "user" :content "hi"}]}
                                 {:provider-config oauth-config})]
            (should= "https://chatgpt.com/backend-api/codex/responses" @captured-url)
            (should= true (:stream @captured-body))
            (should= "You are Codex." (:instructions @captured-body))
            (should= "Hello from Codex" (get-in result [:message :content]))))))

    (it "sanitizes responses input messages to supported keys"
      (let [captured-body (atom nil)
            oauth-config  {:baseUrl "https://api.openai.com/v1" :auth "oauth-device" :name "openai-chatgpt"}
            token         (jwt-with-account-id "acct-123")]
        (with-redefs [llm-http/post-sse!         (fn [_ _ body _ process-event initial & _]
                                                   (reset! captured-body body)
                                                   (process-event {:type "response.completed"
                                                                   :response {:model "gpt-5.4"
                                                                              :usage {:input_tokens 10 :output_tokens 5}}}
                                                                  initial))
                      auth-store/load-tokens    (fn [_ _] {:type "oauth" :access token :expires (+ (System/currentTimeMillis) 60000)})
                      auth-store/token-expired? (fn [_] false)]
          (sut/chat {:model    "gpt-5.4"
                     :system   "You are Codex."
                     :messages [{:role "user" :content "hi" :model "gpt-5.4" :provider "openai-chatgpt"}
                                 {:role "assistant" :content "hello" :model "gpt-5.4"}]}
                     {:provider-config oauth-config})
          (should= [{:role "user" :content "hi"}
                    {:role "assistant" :content "hello"}]
                   (:input @captured-body))
          (should= "You are Codex." (:instructions @captured-body)))))

    (it "adds codex headers for oauth-device tokens"
      (let [captured-headers (atom nil)
             oauth-config     {:baseUrl "https://api.openai.com/v1" :auth "oauth-device" :name "openai-chatgpt"}
             token            (jwt-with-account-id "acct-123")]
        (with-redefs [llm-http/post-sse!         (fn [_ headers _ _ process-event initial & _]
                                                   (reset! captured-headers headers)
                                                   (process-event {:type "response.completed"
                                                                   :response {:model "gpt-5.4"
                                                                              :usage {:input_tokens 10 :output_tokens 5}}}
                                                                  initial))
                      auth-store/load-tokens   (fn [_ _] {:type "oauth" :access token :expires (+ (System/currentTimeMillis) 60000)})
                      auth-store/token-expired? (fn [_] false)]
          (sut/chat {:model "gpt-5.4" :messages [{:role "user" :content "hi"}]} {:provider-config oauth-config})
          (should= "acct-123" (get @captured-headers "ChatGPT-Account-Id"))
          (should= "isaac" (get @captured-headers "originator")))))

    (it "constructs correct URL from baseUrl"
      (let [captured-url (atom nil)]
        (with-redefs [http/post (fn [url _] (reset! captured-url url) (chat-response ""))]
          (sut/chat {:model "test" :messages []} {:provider-config test-config})
          (should= "https://api.example.com/v1/chat/completions" @captured-url))))

    (it "returns auth-failed on 401"
      (with-redefs [http/post (fn [_ _] {:status 401 :body (json/generate-string {:error {:message "invalid"}})})]
        (let [result (sut/chat {:model "test" :messages []} {:provider-config test-config})]
          (should= :auth-failed (:error result)))))

    (it "returns auth-missing when openai api key is blank"
      (let [result (sut/chat {:model "test" :messages []}
                             {:provider-config {:name "openai" :apiKey "" :baseUrl "https://api.openai.com/v1"}})]
        (should= :auth-missing (:error result))
        (should-contain "OPENAI_API_KEY" (:message result))))

    (it "returns auth-missing when grok api key is blank"
      (let [result (sut/chat {:model "test" :messages []}
                             {:provider-config {:name "grok" :apiKey "" :baseUrl "https://api.x.ai/v1"}})]
        (should= :auth-missing (:error result))
        (should-contain "GROK_API_KEY" (:message result))))

    (it "returns auth-missing when oauth-device login is unavailable"
      (with-redefs [auth-store/load-tokens (fn [_ _] nil)]
        (let [result (sut/chat {:model "gpt-5.4" :messages [{:role "user" :content "hi"}]}
                               {:provider-config {:name    "openai-chatgpt"
                                                  :auth    "oauth-device"
                                                  :baseUrl "https://api.openai.com/v1"}})]
          (should= :auth-missing (:error result))
          (should-contain "isaac auth login --provider openai-chatgpt" (:message result)))))

    (it "uses oauth tokens from the configured state directory"
      (let [captured-auth-dir (atom nil)]
        (with-redefs [llm-http/post-sse!         (fn [_ _ _ _ process-event initial & _]
                                                   (process-event {:type "response.completed"
                                                                   :response {:model "gpt-5.4"
                                                                              :usage {:input_tokens 10 :output_tokens 5}}}
                                                                  initial))
                      auth-store/load-tokens    (fn [auth-dir _]
                                                  (reset! captured-auth-dir auth-dir)
                                                  {:type "oauth" :access "token" :expires (+ (System/currentTimeMillis) 60000)})
                      auth-store/token-expired? (fn [_] false)]
          (sut/chat {:model "gpt-5.4" :messages [{:role "user" :content "hi"}]}
                    {:provider-config {:name      "openai-chatgpt"
                                       :auth      "oauth-device"
                                       :baseUrl   "https://api.openai.com/v1"
                                       :state-dir "/tmp/isaac-home/.isaac"}})
          (should= "/tmp/isaac-home/.isaac" @captured-auth-dir))))

    (it "returns connection-refused on ConnectException"
      (with-redefs [http/post (fn [_ _] (throw (java.net.ConnectException.)))]
        (let [result (sut/chat {:model "test" :messages []} {:provider-config test-config})]
          (should= :connection-refused (:error result))))))

  (describe "private helpers"

    (it "returns nil when jwt payload decoding fails"
      (should-be-nil (@#'sut/decode-jwt-payload "x.invalid!.y")))

    (it "uses default codex backend url for oauth-device on api.openai.com"
      (should= "https://chatgpt.com/backend-api/codex"
               (@#'sut/provider-base-url {:auth "oauth-device" :baseUrl "https://api.openai.com/v1"})))

    (it "uses explicit oauth codex baseUrl when it is not the default openai host"
      (should= "https://example.test/codex"
               (@#'sut/provider-base-url {:auth "oauth-device" :baseUrl "https://example.test/codex"})))

    (it "uses completion tokens when prompt tokens are absent"
      (let [usage (@#'sut/parse-usage {:completion_tokens 7})]
        (should= 0 (:input-tokens usage))
        (should= 7 (:output-tokens usage))))

    (it "recognizes oauth-device requests as non chat-completions"
      (should-not (@#'sut/chat-completions-request? {:auth "oauth-device"})))

    (it "builds responses requests without instructions when system is blank"
      (let [result (@#'sut/->responses-request {:model    "gpt-5.4"
                                                 :system   ""
                                                 :messages [{:role "user" :content "hi"}]})]
        (should= {:model "gpt-5.4"
                  :input [{:role "user" :content "hi"}]
                  :store false}
                 result)))

    (it "preserves top-level tools in responses requests"
      (let [tools  [{:type "function" :name "read" :parameters {:type "object"}}]
            result (@#'sut/->responses-request {:model    "gpt-5.4"
                                                :messages [{:role "user" :content "hi"}]
                                                :tools    tools})]
        (should= tools (:tools result))))

    (it "converts assistant tool_calls to function_call items with call_id"
      (let [result (@#'sut/->responses-request {:model    "gpt-5.4"
                                                :messages [{:role "user" :content "what's under the lid?"}
                                                           {:role       "assistant"
                                                            :content    ""
                                                            :tool_calls [{:id       "fc_123"
                                                                          :type     "function"
                                                                          :function {:name      "read"
                                                                                     :arguments "{\"filePath\":\"trash-lid.txt\"}"}}]}
                                                           {:role "tool" :tool_call_id "fc_123" :content "banana peel"}]})]
        (should= {:type "function_call" :call_id "fc_123" :name "read" :arguments "{\"filePath\":\"trash-lid.txt\"}"}
                 (second (:input result)))
        (should= {:type "function_call_output" :call_id "fc_123" :output "banana peel"}
                 (nth (:input result) 2))))

    (it "converts tool role messages to function_call_output items"
      (let [result (@#'sut/->responses-request {:model    "gpt-5.4"
                                                :messages [{:role "user" :content "hi"}
                                                           {:role "tool" :tool_call_id "fc_123" :content {:result "done"}}]})]
        (should= [{:role "user" :content "hi"}
                  {:type "function_call_output" :call_id "fc_123" :output "{\"result\":\"done\"}"}]
                 (:input result))))

    (it "preserves store false on responses requests"
      (let [result (@#'sut/->responses-request {:model    "gpt-5.4"
                                                 :messages [{:role "user" :content "hi"}]})]
        (should= false (:store result))
        (should-not (contains? result :instructions))))

    (it "builds a responses request base with store disabled"
      (should= {:model "gpt-5.4"
                :input [{:role "user" :content "hi"}]
                :store false}
               (@#'sut/responses-request-base "gpt-5.4" [{:role "user" :content "hi"}])))

    (it "increments the tool loop counter"
      (should= 1 (@#'sut/next-loop-count 0)))

    (it "starts tool usage counters at zero"
      (should= {:input-tokens 0 :output-tokens 0}
               (@#'sut/initial-token-counts)))

    (it "starts tool loops at zero"
      (should= 0 (@#'sut/initial-loop-count)))

    (it "continues the tool loop only when calls remain below max-loops"
      (should (@#'sut/continue-tool-loop? [{:id "tc1"}] 0 1))
      (should-not (@#'sut/continue-tool-loop? [{:id "tc1"}] 1 1))
      (should-not (@#'sut/continue-tool-loop? [] 0 1)))

  )

  (describe "chat-with-tools"

    (it "executes tool call loop"
      (let [call-count (atom 0)]
        (with-redefs [http/post (fn [_ _]
                                  (swap! call-count inc)
                                  (if (= 1 @call-count)
                                    (chat-response "" :tool-calls [{:id "tc1"
                                                                    :function {:name "read" :arguments "{\"path\":\"x\"}"}}])
                                    (chat-response "Done")))]
          (let [result (sut/chat-with-tools {:model "test" :messages []} (fn [_ _] "content") {:provider-config test-config})]
            (should= 1 (count (:tool-calls result)))
            (should= "read" (:name (first (:tool-calls result)))))))

    (it "returns the first response when max-loops is zero"
      (with-redefs [http/post (fn [_ _]
                                (chat-response ""
                                               :tool-calls [{:id "tc1"
                                                             :function {:name "read" :arguments "{\"path\":\"x\"}"}}]
                                               :prompt-tokens 11
                                               :completion-tokens 4))]
        (let [result (sut/chat-with-tools {:model "test" :messages []}
                                          (fn [_ _] "content")
                                          {:provider-config test-config :max-loops 0})]
          (should= [] (:tool-calls result))
          (should= 11 (get-in result [:token-counts :input-tokens]))
          (should= false (contains? (get-in result [:response :message]) :tool_calls)))))

    (it "stops collecting tool calls once max-loops is reached"
      (let [call-count (atom 0)
            tool-runs  (atom [])]
        (with-redefs [sut/chat (fn [_ _]
                                 (case (swap! call-count inc)
                                   1 {:message    {:role "assistant" :content ""}
                                      :tool-calls [{:id "tc1" :name "read" :arguments {:path "a"}}]
                                      :usage      {:input-tokens 11 :output-tokens 4}}
                                    2 {:message    {:role "assistant" :content ""}
                                      :tool-calls [{:id "tc2" :name "write" :arguments {:path "b"}}]
                                      :usage      {:input-tokens 3 :output-tokens 2}}
                                    (throw (ex-info "unexpected third tool iteration" {}))))]
          (let [result (sut/chat-with-tools {:model "test" :messages []}
                                            (fn [name _]
                                              (swap! tool-runs conj name)
                                              "content")
                                            {:provider-config test-config :max-loops 1})]
            (should= 1 (count (:tool-calls result)))
            (should= "read" (:name (first (:tool-calls result))))
            (should= 2 @call-count)
            (should= ["read"] @tool-runs)))))

    (it "never makes a third provider call after reaching the loop limit"
      (let [call-count (atom 0)]
        (with-redefs [http/post (fn [_ _]
                                  (case (swap! call-count inc)
                                    1 (chat-response ""
                                                     :tool-calls [{:id "tc1"
                                                                   :function {:name "read" :arguments "{\"path\":\"a\"}"}}]
                                                     :prompt-tokens 11
                                                     :completion-tokens 4)
                                    2 (chat-response ""
                                                     :tool-calls [{:id "tc2"
                                                                   :function {:name "write" :arguments "{\"path\":\"b\"}"}}]
                                                     :prompt-tokens 3
                                                     :completion-tokens 2)
                                    (throw (ex-info "unexpected third provider call" {}))))]
          (let [result (sut/chat-with-tools {:model "test" :messages []}
                                            (fn [_ _] "content")
                                            {:provider-config test-config :max-loops 1})]
            (should= 2 @call-count)
            (should= 1 (count (:tool-calls result)))
            (should= "read" (:name (first (:tool-calls result))))))))

    (it "returns provider errors immediately"
      (with-redefs [sut/chat (fn [& _] {:error :connection-refused})]
        (let [result (sut/chat-with-tools {:model "test" :messages []}
                                          (fn [_ _] "content")
                                          {:provider-config test-config})]
          (should= :connection-refused (:error result))))))

    (it "sends codex tool results as function_call_output items"
      (let [requests      (atom [])
             oauth-config  {:baseUrl "https://api.openai.com/v1" :auth "oauth-device" :name "openai-chatgpt"}
            token         (jwt-with-account-id "acct-123")]
        (with-redefs [llm-http/post-sse!         (fn [_ _ body _ process-event initial & _]
                                                   (swap! requests conj body)
                                                   (if (= 1 (count @requests))
                                                     (reduce (fn [acc evt] (process-event evt acc))
                                                             initial
                                                             [{:type "response.output_item.added"
                                                               :item {:id "fc_123" :type "function_call" :name "read"}}
                                                              {:type "response.function_call_arguments.delta"
                                                               :item_id "fc_123"
                                                               :delta "{\"filePath\":\"trash-lid.txt\"}"}
                                                              {:type "response.function_call_arguments.done"
                                                               :item_id "fc_123"}
                                                              {:type "response.completed"
                                                               :response {:model "gpt-5.4"
                                                                          :usage {:input_tokens 10 :output_tokens 5}}}])
                                                     (reduce (fn [acc evt] (process-event evt acc))
                                                             initial
                                                             [{:type "response.output_text.delta" :delta "done"}
                                                              {:type "response.completed"
                                                               :response {:model "gpt-5.4"
                                                                          :usage {:input_tokens 3 :output_tokens 2}}}])))
                      auth-store/load-tokens    (fn [_ _] {:type "oauth" :access token :expires (+ (System/currentTimeMillis) 60000)})
                      auth-store/token-expired? (fn [_] false)]
          (let [result      (sut/chat-with-tools {:model    "gpt-5.4"
                                                  :messages [{:role "user" :content "what's under the lid?"}]
                                                  :tools    [{:type "function" :name "read" :parameters {:type "object"}}]}
                                                 (fn [_ _] "Old newspaper and a banana peel.")
                                                 {:provider-config oauth-config})
                final-body  (second @requests)]
            (should= "done" (get-in result [:response :message :content]))
            (should= "function_call_output" (get-in final-body [:input 2 :type]))
            (should= "fc_123" (get-in final-body [:input 2 :call_id]))
            (should= "Old newspaper and a banana peel." (get-in final-body [:input 2 :output]))
            (should-be-nil (get-in final-body [:input 2 :role]))))))

  )

  (describe "process-sse-event"

    (it "accumulates delta content"
      (let [acc {:role "assistant" :content "Hi" :model nil :usage {}}
            result (sut/process-sse-event {:choices [{:delta {:content " there"}}]} acc)]
        (should= "Hi there" (:content result))))

    (it "sets model from event"
      (let [acc {:role "assistant" :content "" :model nil :usage {}}
            result (sut/process-sse-event {:model "gpt-5" :choices [{:delta {}}]} acc)]
        (should= "gpt-5" (:model result))))

    (it "sets usage from event"
      (let [acc {:role "assistant" :content "" :model nil :usage {}}
            result (sut/process-sse-event {:usage {:prompt_tokens 10} :choices [{:delta {}}]} acc)]
        (should= 10 (:prompt_tokens (:usage result)))))

    (it "passes through when no relevant fields"
      (let [acc {:role "assistant" :content "x" :model "m" :usage {}}
            result (sut/process-sse-event {:choices [{:delta {}}]} acc)]
        (should= acc result))))

  (describe "process-responses-sse-event"

    (it "accumulates output text deltas"
      (let [acc    {:content "" :model nil :usage {} :response nil}
            result (@#'sut/process-responses-sse-event {:type "response.output_text.delta" :delta "Hello"} acc)]
        (should= "Hello" (:content result))))

    (it "reconstructs tool calls from codex responses events"
      (let [acc     {:content "" :model nil :usage {} :response nil :tool-calls []}
            added   (@#'sut/process-responses-sse-event {:type "response.output_item.added"
                                                         :item {:id "fc_123"
                                                                :type "function_call"
                                                                :name "read"}}
                                                        acc)
            delta   (@#'sut/process-responses-sse-event {:type "response.function_call_arguments.delta"
                                                         :item_id "fc_123"
                                                         :delta "{\"filePath\":\"trash-lid.txt\"}"}
                                                        added)
            done    (@#'sut/process-responses-sse-event {:type "response.function_call_arguments.done"
                                                         :item_id "fc_123"}
                                                        delta)]
        (should= [{:id "fc_123" :name "read" :arguments {:filePath "trash-lid.txt"}}]
                 (:tool-calls done))))

    (it "stores usage and model from response.completed"
      (let [acc    {:content "" :model nil :usage {} :response nil}
            result (@#'sut/process-responses-sse-event {:type "response.completed"
                                                        :response {:model "gpt-5.4"
                                                                   :usage {:input_tokens 10 :output_tokens 5}}}
                                                       acc)]
        (should= "gpt-5.4" (:model result))
        (should= {:input_tokens 10 :output_tokens 5} (:usage result)))))

  (describe "chat-stream"

    (it "streams and accumulates response"
      (let [chunks (atom [])
            captured-body (atom nil)]
        (with-redefs [llm-http/post-sse! (fn [_ _ body on-chunk process-event initial & _]
                                            (reset! captured-body body)
                                             (let [events [{:model "gpt-5" :choices [{:delta {:content "Hello"}}]}
                                                           {:choices [{:delta {:content " world"}}]}
                                                          {:usage {:prompt_tokens 10 :completion_tokens 5} :choices [{:delta {}}]}]]
                                              (reduce (fn [acc evt] (on-chunk evt) (process-event evt acc))
                                                      initial events)))]
          (let [result (sut/chat-stream {:model "gpt-5" :messages []}
                          (fn [c] (swap! chunks conj c))
                          {:provider-config test-config})]
            (should= true (:stream @captured-body))
            (should= "Hello world" (get-in result [:message :content]))
            (should= "gpt-5" (:model result))
            (should= 10 (:input-tokens (:usage result)))
            (should= 3 (count @chunks))))))

     (it "streams codex responses output for oauth-device"
       (let [chunks       (atom [])
             captured-url (atom nil)
             captured-body (atom nil)
             oauth-config {:baseUrl "https://api.openai.com/v1" :auth "oauth-device" :name "openai-chatgpt"}
             token        (jwt-with-account-id "acct-123")]
        (with-redefs [llm-http/post-sse!         (fn [url _ body on-chunk process-event initial & _]
                                                      (reset! captured-url url)
                                                     (reset! captured-body body)
                                                     (let [events [{:type "response.output_text.delta" :delta "Hello"}
                                                                  {:type "response.output_text.delta" :delta " world"}
                                                                  {:type "response.completed"
                                                                   :response {:model "gpt-5.4"
                                                                              :usage {:input_tokens 10 :output_tokens 5}}}]]
                                                     (reduce (fn [acc evt] (on-chunk evt) (process-event evt acc))
                                                             initial events)))
                      auth-store/load-tokens    (fn [_ _] {:type "oauth" :access token :expires (+ (System/currentTimeMillis) 60000)})
                      auth-store/token-expired? (fn [_] false)]
           (let [result (sut/chat-stream {:model "gpt-5.4" :messages [{:role "user" :content "hi"}]}
                                          (fn [c] (swap! chunks conj c))
                                          {:provider-config oauth-config})]
            (should= "https://chatgpt.com/backend-api/codex/responses" @captured-url)
            (should= true (:stream @captured-body))
            (should= "Hello world" (get-in result [:message :content]))
            (should= 2 (count @chunks))))))

     (it "returns codex tool calls parsed from responses SSE events"
       (let [oauth-config {:baseUrl "https://api.openai.com/v1" :auth "oauth-device" :name "openai-chatgpt"}
             token        (jwt-with-account-id "acct-123")]
         (with-redefs [llm-http/post-sse!         (fn [_ _ _ _ process-event initial & _]
                                                    (reduce (fn [acc evt] (process-event evt acc))
                                                            initial
                                                            [{:type "response.output_item.added"
                                                              :item {:id "fc_123" :type "function_call" :name "read"}}
                                                             {:type "response.function_call_arguments.delta"
                                                              :item_id "fc_123"
                                                              :delta "{\"filePath\":\"trash-lid.txt\"}"}
                                                             {:type "response.function_call_arguments.done"
                                                              :item_id "fc_123"}
                                                             {:type "response.completed"
                                                              :response {:model "gpt-5.4"
                                                                         :usage {:input_tokens 10 :output_tokens 5}}}]))
                       auth-store/load-tokens    (fn [_ _] {:type "oauth" :access token :expires (+ (System/currentTimeMillis) 60000)})
                       auth-store/token-expired? (fn [_] false)]
           (let [result (sut/chat {:model    "gpt-5.4"
                                   :messages [{:role "user" :content "what's under the lid?"}]
                                   :tools    [{:type "function" :name "read" :parameters {:type "object"}}]}
                                  {:provider-config oauth-config})]
             (should= [{:id "fc_123" :name "read" :arguments {:filePath "trash-lid.txt"}}]
                      (:tool-calls result))))))

    (it "returns responses streaming errors for oauth-device"
      (let [oauth-config {:baseUrl "https://api.openai.com/v1" :auth "oauth-device" :name "openai-chatgpt"}
            token        (jwt-with-account-id "acct-123")]
        (with-redefs [llm-http/post-sse!         (fn [& _] {:error :api-error})
                      auth-store/load-tokens    (fn [_ _] {:type "oauth" :access token :expires (+ (System/currentTimeMillis) 60000)})
                      auth-store/token-expired? (fn [_] false)]
          (let [result (sut/chat-stream {:model "gpt-5.4" :messages [{:role "user" :content "hi"}]}
                                        identity
                                        {:provider-config oauth-config})]
            (should= :api-error (:error result))))))

    (it "returns error on failure"
      (with-redefs [llm-http/post-sse! (fn [& _] {:error :connection-refused})]
        (let [result (sut/chat-stream {:model "test" :messages []} identity {:provider-config test-config})]
          (should= :connection-refused (:error result)))))

    (it "returns auth-missing when streaming without openai api key"
      (let [result (sut/chat-stream {:model "test" :messages []}
                                    identity
                                    {:provider-config {:name "openai" :apiKey "" :baseUrl "https://api.openai.com/v1"}})]
        (should= :auth-missing (:error result))
        (should-contain "OPENAI_API_KEY" (:message result))))))
