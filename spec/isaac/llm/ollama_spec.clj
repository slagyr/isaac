(ns isaac.llm.ollama-spec
  (:require
    [babashka.http-client :as http]
    [cheshire.core :as json]
    [isaac.llm.ollama :as sut]
    [speclj.core :refer :all]))

(defn- mock-response [body]
  {:status 200 :body (json/generate-string body)})

(defn- mock-stream-response [chunks]
  {:status 200 :body (java.io.ByteArrayInputStream.
                       (.getBytes (apply str (map #(str (json/generate-string %) "\n") chunks))))})

(describe "Ollama Client"

  (describe "chat"

    (it "sends request and parses response"
      (with-redefs [http/post (fn [_ _] (mock-response {:model   "qwen3-coder:30b"
                                                         :message {:role "assistant" :content "Hello!"}
                                                         :done    true
                                                         :prompt_eval_count 10
                                                         :eval_count 5}))]
        (let [result (sut/chat {:model "qwen3-coder:30b" :messages [{:role "user" :content "Hi"}]})]
          (should= "Hello!" (get-in result [:message :content]))
          (should= "qwen3-coder:30b" (:model result)))))

    (it "returns connection-refused on ConnectException"
      (with-redefs [http/post (fn [_ _] (throw (java.net.ConnectException.)))]
        (let [result (sut/chat {:model "test" :messages []})]
          (should= :connection-refused (:error result)))))

    (it "constructs correct URL with base-url"
      (let [captured-url (atom nil)]
        (with-redefs [http/post (fn [url _] (reset! captured-url url) (mock-response {:message {:role "assistant" :content ""}}))]
          (sut/chat {:model "test" :messages []} {:base-url "http://myhost:1234"})
          (should= "http://myhost:1234/api/chat" @captured-url))))

    (it "sets stream to false"
      (let [captured-body (atom nil)]
        (with-redefs [http/post (fn [_ opts] (reset! captured-body (json/parse-string (:body opts) true)) (mock-response {:message {:role "assistant" :content ""}}))]
          (sut/chat {:model "test" :messages []})
          (should= false (:stream @captured-body))))))

  (describe "chat-with-tools"

    (it "returns immediately when no tool calls"
      (with-redefs [http/post (fn [_ _] (mock-response {:model   "test"
                                                         :message {:role "assistant" :content "Done"}
                                                         :done    true
                                                         :prompt_eval_count 10
                                                         :eval_count 5}))]
        (let [result (sut/chat-with-tools {:model "test" :messages []} (fn [_ _] "r"))]
          (should= [] (:tool-calls result))
          (should= "Done" (get-in result [:response :message :content]))
          (should= 10 (:inputTokens (:token-counts result))))))

    (it "executes tool call loop"
      (let [call-count (atom 0)]
        (with-redefs [http/post (fn [_ _]
                                  (swap! call-count inc)
                                  (if (= 1 @call-count)
                                    (mock-response {:model   "test"
                                                    :message {:role       "assistant"
                                                              :content    ""
                                                              :tool_calls [{:function {:name "read" :arguments {:path "x"}}}]}
                                                    :prompt_eval_count 10
                                                    :eval_count 5})
                                    (mock-response {:model   "test"
                                                    :message {:role "assistant" :content "Result"}
                                                    :prompt_eval_count 15
                                                    :eval_count 8})))]
          (let [result (sut/chat-with-tools {:model "test" :messages []} (fn [_ _] "file content"))]
            (should= 1 (count (:tool-calls result)))
            (should= "read" (:name (first (:tool-calls result))))
            (should= 25 (:inputTokens (:token-counts result)))))))))
