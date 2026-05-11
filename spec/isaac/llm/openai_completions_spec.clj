(ns isaac.llm.openai-completions-spec
  (:require
    [babashka.http-client :as http]
    [c3kit.apron.schema :as schema]
    [cheshire.core :as json]
    [isaac.llm.api :as api]
    [isaac.llm.api.openai-completions :as sut]
    [isaac.llm.api.openai.shared :as shared]
    [isaac.llm.http :as llm-http]
    [speclj.core :refer :all]))

(defn- mock-response [body]
  {:status 200 :body (json/generate-string body)})

(defn- chat-response [content & {:keys [model tool-calls prompt-tokens completion-tokens]
                                  :or   {model "gpt-5" prompt-tokens 10 completion-tokens 5}}]
  (mock-response {:choices [{:message (cond-> {:role "assistant" :content content}
                                        tool-calls (assoc :tool_calls tool-calls))}]
                  :model   model
                  :usage   {:prompt_tokens prompt-tokens :completion_tokens completion-tokens}}))

(def test-config {:apiKey "sk-test" :baseUrl "https://api.example.com/v1"})

(describe "OpenAI Completions Provider"

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

    (it "constructs correct URL from baseUrl"
      (let [captured-url (atom nil)]
        (with-redefs [http/post (fn [url _] (reset! captured-url url) (chat-response ""))]
          (sut/chat {:model "test" :messages []} {:provider-config test-config})
          (should= "https://api.example.com/v1/chat/completions" @captured-url))))

    (it "maps :effort 7 to reasoning_effort high"
      (let [captured-body (atom nil)]
        (with-redefs [llm-http/post-json! (fn [_ _ body & _]
                                            (reset! captured-body body)
                                            {:choices [{:message {:role "assistant" :content "ok"}}]
                                             :model   "gpt-5"
                                             :usage   {:prompt_tokens 10 :completion_tokens 5}})]
          (sut/chat {:model "gpt-5" :effort 7 :messages [{:role "user" :content "hi"}]}
                    {:provider-config test-config})
          (should= "high" (:reasoning_effort @captured-body)))))

    (it "omits reasoning_effort when :effort is absent"
      (let [captured-body (atom nil)]
        (with-redefs [llm-http/post-json! (fn [_ _ body & _]
                                            (reset! captured-body body)
                                            {:choices [{:message {:role "assistant" :content "ok"}}]
                                             :model   "cookie"
                                             :usage   {:prompt_tokens 10 :completion_tokens 5}})]
          (sut/chat {:model "cookie" :messages [{:role "user" :content "hi"}]}
                    {:provider-config test-config})
          (should-not (contains? @captured-body :reasoning_effort)))))

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

    (it "returns connection-refused on ConnectException"
      (with-redefs [http/post (fn [_ _] (throw (java.net.ConnectException.)))]
        (let [result (sut/chat {:model "test" :messages []} {:provider-config test-config})]
          (should= :connection-refused (:error result))))))

  (describe "shared helpers"

    (it "returns nil when jwt payload decoding fails"
      (should-be-nil (shared/decode-jwt-payload "x.invalid!.y")))

    (it "uses completion tokens when prompt tokens are absent"
      (let [usage (shared/parse-usage {:completion_tokens 7})]
        (should= 0 (:input-tokens usage))
        (should= 7 (:output-tokens usage)))))

  (describe "followup-messages"

    (it "appends assistant tool_calls in OpenAI function format and role=tool replies"
      (let [tool-calls   [{:id "tc1" :name "read" :arguments {:path "x"}}
                          {:id "tc2" :name "write" :arguments {:path "y" :content "z"}}]
            tool-results ["file contents" "wrote ok"]
            response     {:message {:role "assistant" :content "thinking..."}}
            request      {:messages [{:role "user" :content "go"}]}
            messages     (sut/followup-messages request response tool-calls tool-results)
            assistant    (nth messages 1)]
        (should= 4 (count messages))
        (should= "assistant" (:role assistant))
        (should= "thinking..." (:content assistant))
        (should= "function" (get-in assistant [:tool_calls 0 :type]))
        (should= "tc1" (get-in assistant [:tool_calls 0 :id]))
        (should= "read" (get-in assistant [:tool_calls 0 :function :name]))
        (should= (json/generate-string {:path "x"}) (get-in assistant [:tool_calls 0 :function :arguments]))
        (should= {:role "tool" :tool_call_id "tc1" :content "file contents"} (nth messages 2))
        (should= {:role "tool" :tool_call_id "tc2" :content "wrote ok"} (nth messages 3)))))

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

    (it "returns error on failure"
      (with-redefs [llm-http/post-sse! (fn [& _] {:error :connection-refused})]
        (let [result (sut/chat-stream {:model "test" :messages []} identity {:provider-config test-config})]
          (should= :connection-refused (:error result)))))

    (it "returns auth-missing when streaming without openai api key"
      (let [result (sut/chat-stream {:model "test" :messages []}
                                    identity
                                    {:provider-config {:name "openai" :apiKey "" :baseUrl "https://api.openai.com/v1"}})]
        (should= :auth-missing (:error result))
        (should-contain "OPENAI_API_KEY" (:message result)))))

  (describe "schema conformance"

    (it "chat returns a value conforming to api/response"
      (with-redefs [http/post (fn [_ _] (chat-response "Hello!"))]
        (let [result (sut/chat {:model "test" :messages []} {:provider-config test-config})]
          (should-not (api/error? result))
          (should-not-throw (api/validate-response result)))))

    (it "chat with tool_calls returns a value conforming to api/response"
      (with-redefs [http/post (fn [_ _] (chat-response "" :tool-calls [{:id "tc1"
                                                                         :function {:name "read"
                                                                                    :arguments "{\"path\":\"x\"}"}}]))]
        (let [result (sut/chat {:model "test" :messages []} {:provider-config test-config})]
          (should-not (api/error? result))
          (should-not-throw (api/validate-response result)))))

    (it "chat-stream returns a value conforming to api/response"
      (with-redefs [llm-http/post-sse! (fn [_ _ _ _ process-event initial & _]
                                         (reduce (fn [acc evt] (process-event evt acc))
                                                 initial
                                                 [{:choices [{:delta {:content "hi"}}]}]))]
        (let [result (sut/chat-stream {:model "test" :messages []} identity {:provider-config test-config})]
          (should-not (api/error? result))
          (should-not-throw (api/validate-response result)))))

    (it "auth-missing errors conform to api/error-response"
      (let [result (sut/chat {:model "test" :messages []}
                             {:provider-config {:name "openai" :apiKey "" :baseUrl "https://api.openai.com/v1"}})]
        (should (api/error? result))
        (should-not-throw (schema/conform! api/error-response result))))))
