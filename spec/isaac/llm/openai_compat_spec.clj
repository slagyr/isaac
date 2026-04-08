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
          (should= 42 (:inputTokens (:usage result)))
          (should= 18 (:outputTokens (:usage result))))))

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
            oauth-config     {:baseUrl "https://api.openai.com/v1" :auth "oauth-device" :name "openai-codex"}]
        (with-redefs [http/post          (fn [_ opts] (reset! captured-headers (:headers opts)) (responses-response ""))
                      auth-store/load-tokens (fn [_ provider] {:type     "oauth"
                                                              :access   "header.payload.sig"
                                                              :id-token "id-token"
                                                              :refresh  "rt"
                                                              :expires  (+ (System/currentTimeMillis) 60000)})
                      auth-store/token-expired? (fn [_] false)]
          (sut/chat {:model "test" :messages []} {:provider-config oauth-config})
          (should= "Bearer header.payload.sig" (get @captured-headers "Authorization")))))

    (it "uses chatgpt backend responses endpoint for oauth-device"
      (let [captured-url (atom nil)
            captured-body (atom nil)
            oauth-config {:baseUrl "https://api.openai.com/v1" :auth "oauth-device" :name "openai-codex"}
            token        (jwt-with-account-id "acct-123")]
        (with-redefs [http/post                (fn [url opts] (reset! captured-url url) (reset! captured-body (json/parse-string (:body opts) true)) (responses-response "Hello from Codex"))
                      auth-store/load-tokens   (fn [_ _] {:type "oauth" :access token :expires (+ (System/currentTimeMillis) 60000)})
                      auth-store/token-expired? (fn [_] false)]
          (let [result (sut/chat {:model "gpt-5.4" :messages [{:role "user" :content "hi"}]} {:provider-config oauth-config})]
            (should= "https://chatgpt.com/backend-api/codex/responses" @captured-url)
            (should= false (:store @captured-body))
            (should= "Hello from Codex" (get-in result [:message :content]))))))

    (it "adds chatgpt-account-id header for oauth-device tokens"
      (let [captured-headers (atom nil)
            oauth-config     {:baseUrl "https://api.openai.com/v1" :auth "oauth-device" :name "openai-codex"}
            token            (jwt-with-account-id "acct-123")]
        (with-redefs [http/post                (fn [_ opts] (reset! captured-headers (:headers opts)) (responses-response ""))
                      auth-store/load-tokens   (fn [_ _] {:type "oauth" :access token :expires (+ (System/currentTimeMillis) 60000)})
                      auth-store/token-expired? (fn [_] false)]
          (sut/chat {:model "gpt-5.4" :messages [{:role "user" :content "hi"}]} {:provider-config oauth-config})
          (should= "acct-123" (get @captured-headers "chatgpt-account-id")))))

    (it "constructs correct URL from baseUrl"
      (let [captured-url (atom nil)]
        (with-redefs [http/post (fn [url _] (reset! captured-url url) (chat-response ""))]
          (sut/chat {:model "test" :messages []} {:provider-config test-config})
          (should= "https://api.example.com/v1/chat/completions" @captured-url))))

    (it "returns auth-failed on 401"
      (with-redefs [http/post (fn [_ _] {:status 401 :body (json/generate-string {:error {:message "invalid"}})})]
        (let [result (sut/chat {:model "test" :messages []} {:provider-config test-config})]
          (should= :auth-failed (:error result)))))

    (it "returns connection-refused on ConnectException"
      (with-redefs [http/post (fn [_ _] (throw (java.net.ConnectException.)))]
        (let [result (sut/chat {:model "test" :messages []} {:provider-config test-config})]
          (should= :connection-refused (:error result))))))

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
            (should= "read" (:name (first (:tool-calls result))))))))

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
      (let [chunks (atom [])]
        (with-redefs [llm-http/post-sse! (fn [_ _ _ on-chunk process-event initial]
                                           (let [events [{:model "gpt-5" :choices [{:delta {:content "Hello"}}]}
                                                         {:choices [{:delta {:content " world"}}]}
                                                         {:usage {:prompt_tokens 10 :completion_tokens 5} :choices [{:delta {}}]}]]
                                             (reduce (fn [acc evt] (on-chunk evt) (process-event evt acc))
                                                     initial events)))]
          (let [result (sut/chat-stream {:model "gpt-5" :messages []}
                         (fn [c] (swap! chunks conj c))
                         {:provider-config test-config})]
            (should= "Hello world" (get-in result [:message :content]))
            (should= "gpt-5" (:model result))
            (should= 10 (:inputTokens (:usage result)))
            (should= 3 (count @chunks))))))

    (it "streams responses api output for oauth-device"
      (let [chunks       (atom [])
            captured-url (atom nil)
            oauth-config {:baseUrl "https://api.openai.com/v1" :auth "oauth-device" :name "openai-codex"}
            token        (jwt-with-account-id "acct-123")]
        (with-redefs [llm-http/post-sse!         (fn [url _ _ on-chunk process-event initial]
                                                   (reset! captured-url url)
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
            (should= "Hello world" (get-in result [:message :content]))
            (should= 2 (count @chunks))))))

    (it "returns error on failure"
      (with-redefs [llm-http/post-sse! (fn [_ _ _ _ _ _] {:error :connection-refused})]
        (let [result (sut/chat-stream {:model "test" :messages []} identity {:provider-config test-config})]
          (should= :connection-refused (:error result))))))))
