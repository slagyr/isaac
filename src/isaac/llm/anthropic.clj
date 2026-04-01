(ns isaac.llm.anthropic
  (:require
    [babashka.http-client :as http]
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [isaac.prompt.anthropic :as prompt]))

;; region ----- Auth -----

(defn- auth-headers [{:keys [auth apiKey oauthToken]}]
  (case auth
    "oauth" {"Authorization"     (str "Bearer " oauthToken)
             "anthropic-version" "2023-06-01"
             "content-type"      "application/json"}
    ;; default: api-key
    {"x-api-key"         apiKey
     "anthropic-version" "2023-06-01"
     "content-type"      "application/json"}))

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
      {:error :connection-refused :message "Could not connect to Anthropic API"})
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
          (loop [accumulated {:role "assistant" :content "" :usage {}}]
            (if-let [line (.readLine rdr)]
              (cond
                (str/starts-with? line "data: ")
                (let [data (json/parse-string (subs line 6) true)]
                  (on-chunk data)
                  (case (:type data)
                    "content_block_delta"
                    (recur (update accumulated :content str (get-in data [:delta :text])))

                    "message_delta"
                    (recur (update accumulated :usage merge (:usage data)))

                    "message_start"
                    (recur (assoc accumulated
                             :model (get-in data [:message :model])
                             :usage (get-in data [:message :usage])))

                    ;; Other events: pass through
                    (recur accumulated)))

                :else (recur accumulated))
              accumulated)))))
    (catch java.net.ConnectException _
      {:error :connection-refused :message "Could not connect to Anthropic API"})
    (catch Exception e
      {:error :unknown :message (.getMessage e)})))

;; endregion ^^^^^ HTTP ^^^^^

;; region ----- Response Parsing -----

(defn- extract-text [content-blocks]
  (->> content-blocks
       (filter #(= "text" (:type %)))
       (map :text)
       (str/join "")))

(defn- extract-tool-calls [content-blocks]
  (->> content-blocks
       (filter #(= "tool_use" (:type %)))
       (mapv (fn [block]
               {:type      "toolCall"
                :id        (:id block)
                :name      (:name block)
                :arguments (:input block)}))))

(defn- parse-usage [usage]
  {:inputTokens  (or (:input_tokens usage) 0)
   :outputTokens (or (:output_tokens usage) 0)
   :cacheRead    (or (:cache_read_input_tokens usage) 0)
   :cacheWrite   (or (:cache_creation_input_tokens usage) 0)})

;; endregion ^^^^^ Response Parsing ^^^^^

;; region ----- Public API -----

(defn chat
  "Send a non-streaming Messages API request."
  [request & [{:keys [provider-config] :as opts}]]
  (let [config  (or provider-config {})
        url     (str (or (:baseUrl config) "https://api.anthropic.com") "/v1/messages")
        headers (auth-headers config)
        resp    (post-json! url headers request)]
    (if (:error resp)
      resp
      (let [content   (:content resp)
            text      (extract-text content)
            tools     (extract-tool-calls content)
            usage     (parse-usage (:usage resp))]
        {:message      (cond-> {:role "assistant" :content text}
                         (seq tools) (assoc :tool_calls (mapv (fn [tc]
                                                                {:function {:name      (:name tc)
                                                                            :arguments (:arguments tc)}})
                                                              tools)))
         :model        (:model resp)
         :tool-calls   tools
         :usage        usage
         :_headers     headers
         :stop_reason  (:stop_reason resp)}))))

(defn chat-stream
  "Send a streaming Messages API request via SSE."
  [request on-chunk & [{:keys [provider-config] :as opts}]]
  (let [config  (or provider-config {})
        url     (str (or (:baseUrl config) "https://api.anthropic.com") "/v1/messages")
        headers (auth-headers config)
        body    (assoc request :stream true)
        result  (post-sse! url headers body on-chunk)]
    (if (:error result)
      result
      (let [usage (parse-usage (:usage result))]
        {:message {:role "assistant" :content (:content result)}
         :model   (:model result)
         :usage   usage
         :_headers headers}))))

(defn chat-with-tools
  "Execute a chat with tool call loop."
  [request tool-fn & [{:keys [max-loops] :or {max-loops 10} :as opts}]]
  (loop [req          request
         all-tools    []
         total-usage  {:inputTokens 0 :outputTokens 0 :cacheRead 0 :cacheWrite 0}
         loops        0]
    (let [response (chat req opts)]
      (if (:error response)
        response
        (let [usage      (:usage response)
              merged     (merge-with + total-usage usage)
              tool-calls (:tool-calls response)]
          (if (and (seq tool-calls) (< loops max-loops))
            (let [assistant-msg {:role    "assistant"
                                 :content (mapv (fn [tc]
                                                  {:type  "tool_use"
                                                   :id    (:id tc)
                                                   :name  (:name tc)
                                                   :input (:arguments tc)})
                                                tool-calls)}
                  tool-results  {:role    "user"
                                 :content (mapv (fn [tc]
                                                  {:type        "tool_result"
                                                   :tool_use_id (:id tc)
                                                   :content     (tool-fn (:name tc) (:arguments tc))})
                                                tool-calls)}
                  new-messages  (conj (vec (:messages req)) assistant-msg tool-results)]
              (recur (assoc req :messages new-messages)
                     (into all-tools tool-calls)
                     merged (inc loops)))
            {:response     response
             :tool-calls   all-tools
             :token-counts merged}))))))

;; endregion ^^^^^ Public API ^^^^^
