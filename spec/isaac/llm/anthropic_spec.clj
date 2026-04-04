(ns isaac.llm.anthropic-spec
  (:require
    [babashka.http-client :as http]
    [cheshire.core :as json]
    [isaac.auth.oauth :as oauth]
    [isaac.llm.anthropic :as sut]
    [isaac.llm.http :as llm-http]
    [speclj.core :refer :all]))

(defn- mock-response [body]
  {:status 200 :body (json/generate-string body)})

(defn- api-key-config []
  {:auth "api-key" :apiKey "sk-test" :baseUrl "https://api.anthropic.com"})

(describe "Anthropic Client"

  (describe "chat"

    (it "parses a text response"
      (with-redefs [http/post (fn [_ _] (mock-response {:content    [{:type "text" :text "Hello!"}]
                                                         :model      "claude-sonnet-4-6"
                                                         :stop_reason "end_turn"
                                                         :usage      {:input_tokens 10 :output_tokens 5}}))]
        (let [result (sut/chat {:model "claude-sonnet-4-6" :messages []} {:provider-config (api-key-config)})]
          (should= "Hello!" (get-in result [:message :content]))
          (should= "claude-sonnet-4-6" (:model result))
          (should= 10 (:inputTokens (:usage result)))
          (should= 5 (:outputTokens (:usage result))))))

    (it "extracts tool_use blocks as tool-calls"
      (with-redefs [http/post (fn [_ _] (mock-response {:content    [{:type "text" :text ""}
                                                                       {:type "tool_use" :id "tc1" :name "read_file" :input {:path "README"}}]
                                                         :model      "claude-sonnet-4-6"
                                                         :stop_reason "tool_use"
                                                         :usage      {:input_tokens 10 :output_tokens 5}}))]
        (let [result (sut/chat {:model "claude-sonnet-4-6" :messages []} {:provider-config (api-key-config)})]
          (should= 1 (count (:tool-calls result)))
          (should= "read_file" (:name (first (:tool-calls result))))
          (should= {:path "README"} (:arguments (first (:tool-calls result)))))))

    (it "sets x-api-key header for api-key auth"
      (let [captured-headers (atom nil)]
        (with-redefs [http/post (fn [_ opts] (reset! captured-headers (:headers opts)) (mock-response {:content [] :usage {}}))]
          (sut/chat {:model "test" :messages []} {:provider-config (api-key-config)})
          (should= "sk-test" (get @captured-headers "x-api-key"))
          (should= "2023-06-01" (get @captured-headers "anthropic-version")))))

    (it "sets Bearer and beta headers for OAuth auth"
      (let [captured-headers (atom nil)]
        (with-redefs [http/post   (fn [_ opts] (reset! captured-headers (:headers opts)) (mock-response {:content [] :usage {}}))
                      oauth/resolve-token (fn [_] {:accessToken "sk-ant-oat01-test"})]
          (sut/chat {:model "test" :messages []} {:provider-config {:auth "oauth" :baseUrl "https://api.anthropic.com"}})
          (should= "Bearer sk-ant-oat01-test" (get @captured-headers "Authorization"))
          (should= "claude-code-20250219,oauth-2025-04-20,fine-grained-tool-streaming-2025-05-14,interleaved-thinking-2025-05-14" (get @captured-headers "anthropic-beta"))
          ;; No keyword keys in headers
          (should (every? string? (keys @captured-headers))))))

    (it "tracks cache tokens"
      (with-redefs [http/post (fn [_ _] (mock-response {:content [{:type "text" :text "Hi"}]
                                                         :model   "claude-sonnet-4-6"
                                                         :usage   {:input_tokens 10 :output_tokens 5
                                                                   :cache_read_input_tokens 3
                                                                   :cache_creation_input_tokens 2}}))]
        (let [result (sut/chat {:model "test" :messages []} {:provider-config (api-key-config)})]
          (should= 3 (:cacheRead (:usage result)))
          (should= 2 (:cacheWrite (:usage result))))))

    (it "returns auth-failed on 401"
      (with-redefs [http/post (fn [_ _] {:status 401 :body (json/generate-string {:error {:message "invalid"}})})]
        (let [result (sut/chat {:model "test" :messages []} {:provider-config (api-key-config)})]
          (should= :auth-failed (:error result)))))

    (it "returns connection-refused on ConnectException"
      (with-redefs [http/post (fn [_ _] (throw (java.net.ConnectException.)))]
        (let [result (sut/chat {:model "test" :messages []} {:provider-config (api-key-config)})]
          (should= :connection-refused (:error result))))))

  (describe "chat-with-tools"

    (it "executes tool call loop"
      (let [call-count (atom 0)]
        (with-redefs [http/post (fn [_ _]
                                  (swap! call-count inc)
                                  (if (= 1 @call-count)
                                    (mock-response {:content    [{:type "tool_use" :id "tc1" :name "read" :input {:path "x"}}]
                                                    :model      "claude-sonnet-4-6"
                                                    :stop_reason "tool_use"
                                                    :usage      {:input_tokens 10 :output_tokens 5}})
                                    (mock-response {:content    [{:type "text" :text "Done"}]
                                                    :model      "claude-sonnet-4-6"
                                                    :stop_reason "end_turn"
                                                    :usage      {:input_tokens 15 :output_tokens 8}})))]
          (let [result (sut/chat-with-tools {:model "test" :messages []} (fn [_ _] "content") {:provider-config (api-key-config)})]
            (should= 1 (count (:tool-calls result)))
            (should= "read" (:name (first (:tool-calls result))))
            (should= 25 (:inputTokens (:token-counts result)))))))

  (describe "process-sse-event"

    (it "accumulates content_block_delta"
      (let [acc {:role "assistant" :content "Hello" :usage {}}
            result (sut/process-sse-event {:type "content_block_delta" :delta {:text " world"}} acc)]
        (should= "Hello world" (:content result))))

    (it "merges message_delta usage"
      (let [acc {:role "assistant" :content "" :usage {:output_tokens 5}}
            result (sut/process-sse-event {:type "message_delta" :usage {:output_tokens 10}} acc)]
        (should= 10 (:output_tokens (:usage result)))))

    (it "sets model and usage on message_start"
      (let [acc {:role "assistant" :content "" :usage {}}
            result (sut/process-sse-event {:type "message_start"
                                           :message {:model "claude-sonnet-4-6"
                                                     :usage {:input_tokens 42}}} acc)]
        (should= "claude-sonnet-4-6" (:model result))
        (should= 42 (:input_tokens (:usage result)))))

    (it "passes through unknown event types"
      (let [acc {:role "assistant" :content "x" :usage {}}
            result (sut/process-sse-event {:type "ping"} acc)]
        (should= acc result))))

  (describe "chat-stream"

    (it "streams and accumulates response"
      (let [chunks (atom [])]
        (with-redefs [llm-http/post-sse! (fn [_ _ _ on-chunk process-event initial]
                                           (let [events [{:type "message_start" :message {:model "claude-sonnet-4-6" :usage {:input_tokens 10}}}
                                                         {:type "content_block_delta" :delta {:text "Hello"}}
                                                         {:type "content_block_delta" :delta {:text " world"}}
                                                         {:type "message_delta" :usage {:output_tokens 8}}]]
                                             (reduce (fn [acc evt] (on-chunk evt) (process-event evt acc))
                                                     initial events)))]
          (let [result (sut/chat-stream {:model "claude-sonnet-4-6" :messages []}
                         (fn [c] (swap! chunks conj c))
                         {:provider-config (api-key-config)})]
            (should= "Hello world" (get-in result [:message :content]))
            (should= "claude-sonnet-4-6" (:model result))
            (should= 4 (count @chunks))))))

    (it "returns error on auth failure"
      (with-redefs [llm-http/post-sse! (fn [_ _ _ _ _ _] {:error :auth-failed :status 401})]
        (let [result (sut/chat-stream {:model "test" :messages []} identity {:provider-config (api-key-config)})]
          (should= :auth-failed (:error result))))))))
