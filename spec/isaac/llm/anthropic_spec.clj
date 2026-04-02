(ns isaac.llm.anthropic-spec
  (:require
    [babashka.http-client :as http]
    [cheshire.core :as json]
    [isaac.llm.anthropic :as sut]
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
            (should= 25 (:inputTokens (:token-counts result)))))))))
