(ns isaac.llm.openai-compat
  (:require
    [babashka.http-client :as http]
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]))

;; region ----- Auth -----

(defn- auth-headers [{:keys [apiKey]}]
  (cond-> {"content-type" "application/json"}
    apiKey (assoc "Authorization" (str "Bearer " apiKey))))

;; endregion ^^^^^ Auth ^^^^^

;; region ----- HTTP -----

(defn- post-json! [url headers body]
  (try
    (let [resp (http/post url {:body    (json/generate-string body)
                               :headers headers
                               :timeout 120000
                               :throw   false})]
      (let [parsed (json/parse-string (:body resp) true)]
        (if (>= (:status resp) 400)
          {:error  (if (= 401 (:status resp)) :auth-failed :api-error)
           :status (:status resp)
           :body   parsed}
          (assoc parsed :_headers headers))))
    (catch java.net.ConnectException _
      {:error :connection-refused :message "Could not connect to server"})
    (catch Exception e
      {:error :unknown :message (.getMessage e)})))

(defn- post-sse! [url headers body on-chunk]
  (try
    (let [resp (http/post url {:body    (json/generate-string body)
                               :headers headers
                               :timeout 120000
                               :as      :stream
                               :throw   false})]
      (if (>= (:status resp) 400)
        {:error  (if (= 401 (:status resp)) :auth-failed :api-error)
         :status (:status resp)
         :body   (json/parse-string (slurp (:body resp)) true)}
        (with-open [rdr (io/reader (:body resp))]
          (loop [accumulated {:role "assistant" :content "" :model nil :usage {}}]
            (if-let [line (.readLine rdr)]
              (cond
                (= "data: [DONE]" (str/trim line))
                accumulated

                (str/starts-with? line "data: ")
                (let [data (json/parse-string (subs line 6) true)
                      delta (get-in data [:choices 0 :delta])]
                  (on-chunk data)
                  (recur (cond-> accumulated
                           (:content delta) (update :content str (:content delta))
                           (:model data)    (assoc :model (:model data))
                           (:usage data)    (assoc :usage (:usage data)))))

                :else (recur accumulated))
              accumulated)))))
    (catch java.net.ConnectException _
      {:error :connection-refused :message "Could not connect to server"})
    (catch Exception e
      {:error :unknown :message (.getMessage e)})))

;; endregion ^^^^^ HTTP ^^^^^

;; region ----- Response Parsing -----

(defn- extract-tool-calls [tool-calls]
  (when (seq tool-calls)
    (mapv (fn [tc]
            {:type      "toolCall"
             :id        (:id tc)
             :name      (get-in tc [:function :name])
             :arguments (let [args (get-in tc [:function :arguments])]
                          (if (string? args)
                            (json/parse-string args true)
                            args))})
          tool-calls)))

(defn- parse-usage [usage]
  {:inputTokens  (or (:prompt_tokens usage) 0)
   :outputTokens (or (:completion_tokens usage) 0)})

;; endregion ^^^^^ Response Parsing ^^^^^

;; region ----- Public API -----

(defn chat
  "Send a non-streaming chat completions request."
  [request & [{:keys [provider-config] :as opts}]]
  (let [config  (or provider-config {})
        url     (str (or (:baseUrl config) "http://localhost:11434/v1") "/chat/completions")
        headers (auth-headers config)
        resp    (post-json! url headers request)]
    (if (:error resp)
      resp
      (let [choice     (first (:choices resp))
            msg        (:message choice)
            tool-calls (extract-tool-calls (:tool_calls msg))
            usage      (parse-usage (:usage resp))]
        {:message    (cond-> {:role "assistant" :content (or (:content msg) "")}
                       (seq tool-calls) (assoc :tool_calls (mapv (fn [tc]
                                                                   {:function {:name      (:name tc)
                                                                               :arguments (:arguments tc)}})
                                                                 tool-calls)))
         :model      (:model resp)
         :tool-calls tool-calls
         :usage      usage
         :_headers   headers}))))

(defn chat-stream
  "Send a streaming chat completions request via SSE."
  [request on-chunk & [{:keys [provider-config] :as opts}]]
  (let [config  (or provider-config {})
        url     (str (or (:baseUrl config) "http://localhost:11434/v1") "/chat/completions")
        headers (auth-headers config)
        body    (assoc request :stream true)
        result  (post-sse! url headers body on-chunk)]
    (if (:error result)
      result
      (let [usage (parse-usage (:usage result))]
        {:message  {:role "assistant" :content (:content result)}
         :model    (:model result)
         :usage    usage
         :_headers headers}))))

(defn chat-with-tools
  "Execute a chat with tool call loop."
  [request tool-fn & [{:keys [max-loops] :or {max-loops 10} :as opts}]]
  (loop [req          request
         all-tools    []
         total-usage  {:inputTokens 0 :outputTokens 0}
         loops        0]
    (let [response (chat req opts)]
      (if (:error response)
        response
        (let [usage      (:usage response)
              merged     (merge-with + total-usage usage)
              tool-calls (:tool-calls response)]
          (if (and (seq tool-calls) (< loops max-loops))
            (let [assistant-msg {:role       "assistant"
                                 :content    (get-in response [:message :content])
                                 :tool_calls (mapv (fn [tc]
                                                     {:id       (:id tc)
                                                      :type     "function"
                                                      :function {:name      (:name tc)
                                                                 :arguments (json/generate-string (:arguments tc))}})
                                                   tool-calls)}
                  tool-results  (mapv (fn [tc]
                                        {:role         "tool"
                                         :tool_call_id (:id tc)
                                         :content      (tool-fn (:name tc) (:arguments tc))})
                                      tool-calls)
                  new-messages  (into (conj (vec (:messages req)) assistant-msg) tool-results)]
              (recur (assoc req :messages new-messages)
                     (into all-tools tool-calls)
                     merged (inc loops)))
            {:response     response
             :tool-calls   all-tools
             :token-counts merged}))))))

;; endregion ^^^^^ Public API ^^^^^
