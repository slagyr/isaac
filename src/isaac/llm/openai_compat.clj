(ns isaac.llm.openai-compat
  (:require
    [cheshire.core :as json]
    [isaac.auth.store :as auth-store]
    [isaac.llm.http :as llm-http]))

;; region ----- Auth -----

(defn- resolve-oauth-token [{:keys [auth name]}]
  (when (= "oauth-device" auth)
    (let [state-dir (str (System/getProperty "user.home") "/.isaac")
          tokens    (auth-store/load-tokens state-dir name)]
      (when (and tokens (not (auth-store/token-expired? tokens)))
        (:access tokens)))))

(defn- auth-headers [config]
  (let [oauth-token (resolve-oauth-token config)
        api-key     (:apiKey config)
        token       (or oauth-token api-key)]
    (cond-> {"content-type" "application/json"}
      token (assoc "Authorization" (str "Bearer " token)))))

;; endregion ^^^^^ Auth ^^^^^

;; region ----- SSE Event Processing -----

(defn process-sse-event
  "Accumulate an OpenAI-compatible SSE event into the running state."
  [data accumulated]
  (let [delta (get-in data [:choices 0 :delta])]
    (cond-> accumulated
      (:content delta) (update :content str (:content delta))
      (:model data)    (assoc :model (:model data))
      (:usage data)    (assoc :usage (:usage data)))))

;; endregion ^^^^^ SSE Event Processing ^^^^^

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
        resp    (llm-http/post-json! url headers request)]
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
        initial {:role "assistant" :content "" :model nil :usage {}}
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
