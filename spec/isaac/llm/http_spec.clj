(ns isaac.llm.http-spec
  (:require
    [babashka.http-client :as http]
    [cheshire.core :as json]
    [isaac.llm.http :as sut]
    [speclj.core :refer :all]))

(defn- mock-response [status body]
  {:status status :body (json/generate-string body)})

(defn- mock-stream-response [status lines]
  {:status status
   :body   (java.io.ByteArrayInputStream.
             (.getBytes (apply str (map #(str % "\n") lines))))})

(describe "LLM HTTP"

  (describe "post-json!"

    (it "returns parsed response on success"
      (with-redefs [http/post (fn [_ _] (mock-response 200 {:result "ok"}))]
        (let [result (sut/post-json! "http://test" {} {})]
          (should= "ok" (:result result)))))

    (it "returns :auth-failed on 401"
      (with-redefs [http/post (fn [_ _] (mock-response 401 {:error {:message "bad key"}}))]
        (let [result (sut/post-json! "http://test" {} {})]
          (should= :auth-failed (:error result))
          (should= 401 (:status result)))))

    (it "returns :api-error on other 4xx/5xx"
      (with-redefs [http/post (fn [_ _] (mock-response 500 {:error {:message "server down"}}))]
        (let [result (sut/post-json! "http://test" {} {})]
          (should= :api-error (:error result))
          (should= 500 (:status result)))))

    (it "returns :connection-refused on ConnectException"
      (with-redefs [http/post (fn [_ _] (throw (java.net.ConnectException.)))]
        (let [result (sut/post-json! "http://test" {} {})]
          (should= :connection-refused (:error result)))))

    (it "returns :unknown on other exceptions"
      (with-redefs [http/post (fn [_ _] (throw (Exception. "boom")))]
        (let [result (sut/post-json! "http://test" {} {})]
          (should= :unknown (:error result))
          (should= "boom" (:message result)))))

    (it "passes headers and serialized body"
      (let [captured (atom nil)]
        (with-redefs [http/post (fn [url opts] (reset! captured {:url url :opts opts}) (mock-response 200 {}))]
          (sut/post-json! "http://test/api" {"x-key" "abc"} {:model "m"})
          (should= "http://test/api" (:url @captured))
          (should= {"x-key" "abc"} (:headers (:opts @captured)))
          (should= {:model "m"} (json/parse-string (:body (:opts @captured)) true))))))

  (describe "process-sse-lines"

    (it "accumulates SSE data lines via process-event"
      (let [chunks (atom [])
            result (sut/process-sse-lines
                     ["data: {\"text\":\"Hello\"}" "data: {\"text\":\" world\"}"]
                     (fn [data] (swap! chunks conj data))
                     (fn [data acc] (str acc (:text data)))
                     "")]
        (should= "Hello world" result)
        (should= 2 (count @chunks))))

    (it "stops at [DONE] marker"
      (let [chunks (atom [])
            result (sut/process-sse-lines
                     ["data: {\"text\":\"Hi\"}" "data: [DONE]" "data: {\"text\":\"ignored\"}"]
                     (fn [data] (swap! chunks conj data))
                     (fn [data acc] (str acc (:text data)))
                     "")]
        (should= "Hi" result)
        (should= 1 (count @chunks))))

    (it "skips non-data lines"
      (let [result (sut/process-sse-lines
                     ["event: ping" "data: {\"n\":1}" ": comment" "data: {\"n\":2}"]
                     (fn [_])
                     (fn [data acc] (+ acc (:n data)))
                     0)]
        (should= 3 result))))

  (describe "post-sse!"

    (it "processes SSE stream and returns accumulated result"
      (with-redefs [http/post (fn [_ _] (mock-stream-response 200
                                           ["data: {\"text\":\"A\"}"
                                            "data: {\"text\":\"B\"}"
                                            "data: [DONE]"]))]
        (let [chunks (atom [])
              result (sut/post-sse! "http://test" {} {} (fn [d] (swap! chunks conj d))
                       (fn [data acc] (str acc (:text data))) "")]
          (should= "AB" result)
          (should= 2 (count @chunks)))))

    (it "returns :auth-failed on 401"
      (with-redefs [http/post (fn [_ _] (mock-stream-response 401 ["{\"error\":\"bad\"}"]))]
        ;; Need a proper error body for the slurp path
        (with-redefs [http/post (fn [_ _] {:status 401
                                            :body   (java.io.ByteArrayInputStream.
                                                      (.getBytes (json/generate-string {:error "bad"})))})]
          (let [result (sut/post-sse! "http://test" {} {} identity (fn [_ a] a) nil)]
            (should= :auth-failed (:error result))))))

    (it "returns :connection-refused on ConnectException"
      (with-redefs [http/post (fn [_ _] (throw (java.net.ConnectException.)))]
        (let [result (sut/post-sse! "http://test" {} {} identity (fn [_ a] a) nil)]
          (should= :connection-refused (:error result))))))

  (describe "post-ndjson-stream!"

    (it "processes newline-delimited JSON stream"
      (with-redefs [http/post (fn [_ _] (mock-stream-response 200
                                           ["{\"message\":{\"content\":\"Hi\"},\"done\":false}"
                                            "{\"message\":{\"content\":\"!\"},\"done\":true}"]))]
        (let [chunks (atom [])
              result (sut/post-ndjson-stream! "http://test" {} {} (fn [c] (swap! chunks conj c)))]
          (should= true (:done result))
          (should= 2 (count @chunks)))))

    (it "skips blank lines"
      (with-redefs [http/post (fn [_ _] (mock-stream-response 200
                                           ["{\"n\":1}" "" "{\"n\":2}"]))]
        (let [chunks (atom [])
              result (sut/post-ndjson-stream! "http://test" {} {} (fn [c] (swap! chunks conj c)))]
          (should= 2 (:n result))
          (should= 2 (count @chunks)))))

    (it "returns :connection-refused on ConnectException"
      (with-redefs [http/post (fn [_ _] (throw (java.net.ConnectException.)))]
        (let [result (sut/post-ndjson-stream! "http://test" {} {} identity)]
          (should= :connection-refused (:error result)))))))
