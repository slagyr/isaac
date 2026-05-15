(ns isaac.llm.api.ollama
  (:require
    [isaac.llm.api :as api]
    [isaac.llm.followup :as followup]
    [isaac.llm.http :as llm-http]
    [isaac.llm.prompt.builder :as prompt]))

;; region ----- Effort Translation -----

(defn- effort->think [effort think-mode]
  (case (or think-mode :bool)
    :bool   (when (some? effort) (pos? effort))
    :levels (cond
              (or (nil? effort) (zero? effort)) nil
              (<= 1 effort 3)                   "low"
              (<= 4 effort 6)                   "medium"
              :else                             "high")))

;; endregion ^^^^^ Effort Translation ^^^^^

;; region ----- Public API -----

(def ^:private default-headers {"Content-Type" "application/json"})

(def ^:private default-timeout 300000)

(defn- http-opts [{:keys [session-key simulate-provider timeout]}]
  (cond-> {:timeout (or timeout default-timeout)}
    session-key       (assoc :session-key session-key)
    simulate-provider (assoc :simulate-provider simulate-provider)))

(defn chat
  "Send a chat request to Ollama. Returns the parsed response or error map."
  [request & [{:keys [base-url think-mode] :or {base-url "http://localhost:11434"} :as opts}]]
  (let [url   (str base-url "/api/chat")
        think (effort->think (:effort request) think-mode)
        body  (cond-> (-> request (dissoc :effort) (assoc :stream false))
                (some? think) (assoc :think think))]
    (llm-http/post-json! url default-headers body (http-opts opts))))

(defn chat-stream
  "Send a streaming chat request to Ollama. Calls on-chunk for each chunk.
   Returns the final response or error map."
  [request on-chunk & [{:keys [base-url think-mode] :or {base-url "http://localhost:11434"} :as opts}]]
  (let [url   (str base-url "/api/chat")
        think (effort->think (:effort request) think-mode)
        body  (cond-> (-> request (dissoc :effort) (assoc :stream true))
                (some? think) (assoc :think think))]
    (llm-http/post-ndjson-stream! url default-headers body on-chunk (http-opts opts))))

;; endregion ^^^^^ Public API ^^^^^

;; region ----- Tool Call Loop -----

(defn followup-messages
  "Build the next iteration's :messages vector for Ollama's /api/chat.
   Assistant message carries the raw tool_calls; tool responses are role=tool."
  [request response tool-calls tool-results]
  (followup/raw-tool-call-followup-messages
    request
    {:role       "assistant"
     :content    (or (get-in response [:message :content]) "")
     :tool_calls (get-in response [:message :tool_calls])}
    tool-calls
    tool-results))

(deftype OllamaAPI [provider-name opts cfg]
  api/Api
  (chat [_ req] (#'chat req opts))
  (chat-stream [_ req on-chunk] (#'chat-stream req on-chunk opts))
  (followup-messages [_ req resp tcs trs] (#'followup-messages req resp tcs trs))
  (config [_] cfg)
  (display-name [_] provider-name)
  (build-prompt [_ opts] (prompt/build opts)))

(defn make [name cfg]
  (->OllamaAPI name (api/ollama-opts cfg) cfg))

;; endregion ^^^^^ Tool Call Loop ^^^^^
