(ns isaac.llm.anthropic
  (:require
    [clojure.string :as str]
    [isaac.api.provider :as api-provider]
    [isaac.llm.http :as llm-http]
    [isaac.prompt.anthropic :as prompt]
    [isaac.provider :as provider]))

;; region ----- Auth -----

(defn- missing-auth-error [{:keys [apiKey]}]
  (when (str/blank? apiKey)
    {:error   :auth-missing
     :message "Missing Anthropic API key. Set ANTHROPIC_API_KEY or configure provider :apiKey."}))

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
  {:input-tokens  (or (:input_tokens usage) 0)
   :output-tokens (or (:output_tokens usage) 0)
   :cache-read    (or (:cache_read_input_tokens usage) 0)
   :cache-write   (or (:cache_creation_input_tokens usage) 0)})

;; endregion ^^^^^ Response Parsing ^^^^^

;; region ----- Public API -----

(defn chat
  "Send a non-streaming Messages API request."
  [request & [{:keys [provider-config] :as opts}]]
  (let [config  (or provider-config {})
        url     (str (or (:baseUrl config) "https://api.anthropic.com") "/v1/messages")
        auth-err (missing-auth-error config)]
    (if auth-err
      auth-err
      (let [headers (auth-headers config)
            resp    (if-let [session-key (:session-key config)]
                      (llm-http/post-json! url headers request {:session-key session-key})
                      (llm-http/post-json! url headers request))]
        (if (:error resp)
          resp
          (let [content (:content resp)
                text    (extract-text content)
                tools   (extract-tool-calls content)
                usage   (parse-usage (:usage resp))]
            {:message     (cond-> {:role "assistant" :content text}
                            (seq tools) (assoc :tool_calls (mapv (fn [tc]
                                                                   {:function {:name      (:name tc)
                                                                               :arguments (:arguments tc)}})
                                                                 tools)))
             :model       (:model resp)
             :tool-calls  tools
             :usage       usage
             :_headers    headers
             :stop_reason (:stop_reason resp)}))))))

(defn chat-stream
  "Send a streaming Messages API request via SSE."
  [request on-chunk & [{:keys [provider-config] :as opts}]]
  (let [config  (or provider-config {})
        url     (str (or (:baseUrl config) "https://api.anthropic.com") "/v1/messages")
        auth-err (missing-auth-error config)]
    (if auth-err
      auth-err
      (let [headers (auth-headers config)
            body    (assoc request :stream true)
            initial {:role "assistant" :content "" :usage {}}
            result  (if-let [session-key (:session-key config)]
                      (llm-http/post-sse! url headers body on-chunk process-sse-event initial {:session-key session-key})
                      (llm-http/post-sse! url headers body on-chunk process-sse-event initial))]
        (if (:error result)
          result
          (let [usage (parse-usage (:usage result))]
            {:message  {:role "assistant" :content (:content result)}
             :model    (:model result)
             :usage    usage
             :_headers headers}))))))

(defn followup-messages
  "Build the next iteration's :messages vector for the Anthropic Messages API.
   Pairs tool_use blocks (in an assistant message) with tool_result blocks
   (in a single user message)."
  [request _response tool-calls tool-results]
  (let [assistant-msg {:role    "assistant"
                       :content (mapv (fn [tc]
                                        {:type  "tool_use"
                                         :id    (:id tc)
                                         :name  (:name tc)
                                         :input (:arguments tc)})
                                      tool-calls)}
        tool-result   {:role    "user"
                       :content (mapv (fn [tc result]
                                        {:type        "tool_result"
                                         :tool_use_id (:id tc)
                                         :content     result})
                                      tool-calls
                                      tool-results)}]
    (conj (vec (:messages request)) assistant-msg tool-result)))

(deftype AnthropicProvider [provider-name opts cfg]
  provider/Provider
  (chat [_ req] (#'chat req opts))
  (chat-stream [_ req on-chunk] (#'chat-stream req on-chunk opts))
  (followup-messages [_ req resp tcs trs] (#'followup-messages req resp tcs trs))
  (config [_] cfg)
  (display-name [_] provider-name))

(defn make [name cfg]
  (->AnthropicProvider name (provider/wire-opts cfg) cfg))

(defonce _registration
  ;; Both apis route here: "anthropic-messages" is internal, "anthropic" is the
  ;; user-facing alias accepted in :api config.
  (do (api-provider/register-provider! "anthropic-messages" make)
      (api-provider/register-provider! "anthropic" make)))

;; endregion ^^^^^ Public API ^^^^^
