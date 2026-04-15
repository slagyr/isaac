(ns isaac.llm.http
  (:require
    [babashka.http-client :as http]
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [isaac.llm.grover :as grover]
    [isaac.session.bridge :as bridge]))

(def ^:private pending ::pending)

(defn- simulated-provider? [opts]
  (boolean (:simulate-provider opts)))

(defn- cancellable-call [session-key f]
  (let [runner (future (f))]
    (loop []
      (let [result (deref runner 50 pending)]
        (if (= pending result)
          (if (bridge/cancelled? session-key)
            (do
              (future-cancel runner)
              {:error :cancelled})
            (recur))
          result)))))

(defn post-json!
  "POST JSON to a URL with headers. Returns parsed response or error map.
   Checks HTTP status codes: 401 -> :auth-failed, 4xx/5xx -> :api-error."
  [url headers body & [{:keys [session-key simulate-provider timeout] :or {timeout 120000}}]]
  (if (simulated-provider? {:simulate-provider simulate-provider})
    (grover/post-json! simulate-provider url headers body)
    (cancellable-call session-key
                     #(try
                        (let [resp (http/post url {:body    (json/generate-string body)
                                                   :headers headers
                                                  :timeout timeout
                                                  :throw   false})]
                         (let [parsed (json/parse-string (:body resp) true)]
                           (if (>= (:status resp) 400)
                             {:error    (if (= 401 (:status resp)) :auth-failed :api-error)
                              :status   (:status resp)
                              :body     parsed
                              :_headers headers}
                             parsed)))
                       (catch java.net.ConnectException _
                         {:error :connection-refused :message (str "Could not connect to " url)})
                        (catch IllegalArgumentException _
                          {:error :connection-refused :message (str "Could not connect to " url)})
                        (catch Exception e
                          {:error :unknown :message (.getMessage e)})))))

(defn process-sse-lines
  "Process SSE lines, calling on-chunk and accumulating via process-event.
   Returns the final accumulated value. Pure data transformation over lines."
  [lines on-chunk process-event initial]
  (reduce
    (fn [accumulated line]
      (cond
        (= "[DONE]" (str/trim (subs line 6)))
        (reduced accumulated)

        :else
        (let [data (json/parse-string (subs line 6) true)]
          (on-chunk data)
          (process-event data accumulated))))
    initial
    (filter #(str/starts-with? % "data: ") lines)))

(defn post-sse!
  "POST and process SSE stream. Calls on-chunk for each parsed event.
   process-event is (fn [data accumulated] -> accumulated) for custom accumulation."
  [url headers body on-chunk process-event initial & [{:keys [session-key simulate-provider timeout] :or {timeout 120000}}]]
  (if (simulated-provider? {:simulate-provider simulate-provider})
    (grover/post-sse! simulate-provider url headers body on-chunk process-event initial)
    (cancellable-call session-key
                     #(try
                        (let [resp (http/post url {:body    (json/generate-string body)
                                                   :headers headers
                                                  :timeout timeout
                                                  :as      :stream
                                                  :throw   false})]
                         (if (>= (:status resp) 400)
                           {:error    (if (= 401 (:status resp)) :auth-failed :api-error)
                            :status   (:status resp)
                            :body     (try (json/parse-string (slurp (:body resp)) true)
                                           (catch Exception _ nil))
                            :_headers headers}
                           (with-open [rdr (io/reader (:body resp))]
                             (process-sse-lines (line-seq rdr) on-chunk process-event initial))))
                        (catch java.net.ConnectException _
                          {:error :connection-refused :message (str "Could not connect to " url)})
                        (catch Exception e
                          {:error :unknown :message (.getMessage e)})))))

(defn post-ndjson-stream!
  "POST and process newline-delimited JSON stream (Ollama-style).
   Calls on-chunk for each parsed line. Returns the final chunk."
  [url headers body on-chunk & [{:keys [session-key timeout] :or {timeout 120000}}]]
  (cancellable-call session-key
                    #(try
                       (let [resp (http/post url {:body    (json/generate-string body)
                                                  :headers headers
                                                  :timeout timeout
                                                  :as      :stream
                                                  :throw   false})]
                         (if (>= (:status resp) 400)
                           {:error    (if (= 401 (:status resp)) :auth-failed :api-error)
                            :status   (:status resp)
                            :body     (try (json/parse-string (slurp (:body resp)) true)
                                           (catch Exception _ nil))
                            :_headers headers}
                           (with-open [rdr (io/reader (:body resp))]
                             (loop [last-chunk nil]
                               (if-let [line (.readLine rdr)]
                                 (if (str/blank? line)
                                   (recur last-chunk)
                                   (let [chunk (json/parse-string line true)]
                                     (on-chunk chunk)
                                     (recur chunk)))
                                 last-chunk)))))
                       (catch java.net.ConnectException _
                         {:error :connection-refused :message (str "Could not connect to " url)})
                       (catch IllegalArgumentException _
                         {:error :connection-refused :message (str "Could not connect to " url)})
                       (catch Exception e
                         {:error :unknown :message (.getMessage e)}))))
