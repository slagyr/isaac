(ns isaac.llm.anthropic
  (:require
    [clojure.string :as str]
    [isaac.llm.http :as llm-http]
    [isaac.prompt.anthropic :as prompt]))

;; region ----- Auth -----

(defn- auth-headers [{:keys [apiKey]}]
  {"x-api-key"         apiKey
   "anthropic-version" "2023-06-01"
   "content-type"      "application/json"})

;; endregion ^^^^^ Auth ^^^^^

;; region ----- SSE Event Processing -----

(defn process-sse-event
  "Accumulate an Anthropic SSE event into the running state."
  [data accumulated]
  (case (:type data)
    "content_block_delta"
    (update accumulated :content str (get-in data [:delta :text]))

    "message_delta"
    (update accumulated :usage merge (:usage data))

    "message_start"
    (assoc accumulated
      :model (get-in data [:message :model])
      :usage (get-in data [:message :usage]))

    ;; Other events: pass through
    accumulated))

;; endregion ^^^^^ SSE Event Processing ^^^^^

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
        resp    (llm-http/post-json! url headers request)]
    (if (:error resp)
      resp
      (let [content (:content resp)
            text    (extract-text content)
            tools   (extract-tool-calls content)
            usage   (parse-usage (:usage resp))]
        {:message    (cond-> {:role "assistant" :content text}
                       (seq tools) (assoc :tool_calls (mapv (fn [tc]
                                                              {:function {:name      (:name tc)
                                                                          :arguments (:arguments tc)}})
                                                            tools)))
         :model      (:model resp)
         :tool-calls tools
         :usage      usage
         :_headers   headers
         :stop_reason (:stop_reason resp)}))))

(defn chat-stream
  "Send a streaming Messages API request via SSE."
  [request on-chunk & [{:keys [provider-config] :as opts}]]
  (let [config  (or provider-config {})
        url     (str (or (:baseUrl config) "https://api.anthropic.com") "/v1/messages")
        headers (auth-headers config)
        body    (assoc request :stream true)
        initial {:role "assistant" :content "" :usage {}}
        result  (llm-http/post-sse! url headers body on-chunk process-sse-event initial)]
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
