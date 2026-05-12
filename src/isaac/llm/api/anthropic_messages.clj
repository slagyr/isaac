(ns isaac.llm.api.anthropic-messages
  (:require
    [clojure.string :as str]
    [isaac.llm.api :as api]
    [isaac.llm.followup :as followup]
    [isaac.llm.http :as llm-http]
    [isaac.llm.prompt.anthropic :as anthropic-prompt]))

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

;; region ----- Effort Translation -----

(defn- effort->thinking [effort budget-max]
  (when (and effort (pos? effort))
    {:type          "enabled"
     :budget_tokens (int (* effort (/ (or budget-max 32000) 10)))}))

;; endregion ^^^^^ Effort Translation ^^^^^

;; region ----- Public API -----

(defn- http-opts [config]
  (cond-> {}
    (:session-key config)       (assoc :session-key (:session-key config))
    (:simulate-provider config) (assoc :simulate-provider (:simulate-provider config))))

(defn chat
  "Send a non-streaming Messages API request."
  [request & [{:keys [provider-config]}]]
  (let [config   (or provider-config {})
        url      (str (or (:baseUrl config) "https://api.anthropic.com") "/v1/messages")
        auth-err (missing-auth-error config)]
    (if auth-err
      auth-err
      (let [headers  (auth-headers config)
            thinking (effort->thinking (:effort request) (:thinking-budget-max config))
            body     (cond-> (dissoc request :effort)
                       thinking (assoc :thinking thinking))
            resp     (llm-http/post-json! url headers body (http-opts config))]
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
  [request on-chunk & [{:keys [provider-config]}]]
  (let [config   (or provider-config {})
        url      (str (or (:baseUrl config) "https://api.anthropic.com") "/v1/messages")
        auth-err (missing-auth-error config)]
    (if auth-err
      auth-err
      (let [headers  (auth-headers config)
            thinking (effort->thinking (:effort request) (:thinking-budget-max config))
            body     (cond-> (-> request (dissoc :effort) (assoc :stream true))
                       thinking (assoc :thinking thinking))
            initial  {:role "assistant" :content "" :usage {}}
            result   (llm-http/post-sse! url headers body on-chunk process-sse-event initial (http-opts config))]
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
                       :content (followup/map-tool-results tool-calls tool-results
                                                           (fn [tc result]
                                                             {:type        "tool_result"
                                                              :tool_use_id (:id tc)
                                                              :content     result}))}]
    (followup/append-followup-messages request assistant-msg [tool-result])))

(deftype AnthropicProvider [provider-name opts cfg]
  api/Api
  (chat [_ req] (#'chat req opts))
  (chat-stream [_ req on-chunk] (#'chat-stream req on-chunk opts))
  (followup-messages [_ req resp tcs trs] (#'followup-messages req resp tcs trs))
  (config [_] cfg)
  (display-name [_] provider-name)
  (build-prompt [_ opts] (anthropic-prompt/build opts)))

(defn make [name cfg]
  (->AnthropicProvider name (api/wire-opts cfg) cfg))

(defn -isaac-init []
  ;; Both apis route here: "anthropic-messages" is internal, "anthropic" is the
  ;; user-facing alias accepted in :api config.
  (api/register! :anthropic-messages make)
  (api/register! :anthropic make))

;; endregion ^^^^^ Public API ^^^^^
