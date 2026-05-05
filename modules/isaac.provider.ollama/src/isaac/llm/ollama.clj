(ns isaac.llm.ollama
  (:require
    [isaac.llm.http :as llm-http]
    [isaac.provider :as provider]))

;; region ----- Public API -----

(def ^:private default-headers {"Content-Type" "application/json"})

(def ^:private default-timeout 300000)

(defn chat
  "Send a chat request to Ollama. Returns the parsed response or error map."
  [request & [{:keys [base-url session-key timeout] :or {base-url "http://localhost:11434"
                                                         timeout  default-timeout}}]]
  (let [url  (str base-url "/api/chat")
        body (assoc request :stream false)]
    (if session-key
      (llm-http/post-json! url default-headers body {:session-key session-key
                                                     :timeout     timeout})
      (llm-http/post-json! url default-headers body {:timeout timeout}))))

(defn chat-stream
  "Send a streaming chat request to Ollama. Calls on-chunk for each chunk.
   Returns the final response or error map."
  [request on-chunk & [{:keys [base-url session-key timeout] :or {base-url "http://localhost:11434"
                                                                  timeout  default-timeout}}]]
  (let [url  (str base-url "/api/chat")
        body (assoc request :stream true)]
    (if session-key
      (llm-http/post-ndjson-stream! url default-headers body on-chunk {:session-key session-key
                                                                       :timeout     timeout})
      (llm-http/post-ndjson-stream! url default-headers body on-chunk {:timeout timeout}))))

;; endregion ^^^^^ Public API ^^^^^

;; region ----- Tool Call Loop -----

(defn followup-messages
  "Build the next iteration's :messages vector for Ollama's /api/chat.
   Assistant message carries the raw tool_calls; tool responses are role=tool."
  [request response tool-calls tool-results]
  (let [assistant-msg {:role       "assistant"
                       :content    (or (get-in response [:message :content]) "")
                       :tool_calls (get-in response [:message :tool_calls])}
        result-msgs   (mapv (fn [_tc result]
                              {:role    "tool"
                               :content result})
                            tool-calls
                            tool-results)]
    (into (vec (:messages request)) (cons assistant-msg result-msgs))))

(deftype OllamaProvider [provider-name opts cfg]
  provider/Provider
  (chat [_ req] (#'chat req opts))
  (chat-stream [_ req on-chunk] (#'chat-stream req on-chunk opts))
  (followup-messages [_ req resp tcs trs] (#'followup-messages req resp tcs trs))
  (config [_] cfg)
  (display-name [_] provider-name))

(defn make [name cfg]
  (->OllamaProvider name (provider/ollama-opts cfg) cfg))

(provider/register! "ollama" make)

;; endregion ^^^^^ Tool Call Loop ^^^^^
