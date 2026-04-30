(ns isaac.llm.ollama
  (:require
    [isaac.llm.http :as llm-http]))

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

(defn- has-tool-calls? [response]
  (seq (get-in response [:message :tool_calls])))

(defn- extract-tool-calls
  "Extract tool calls from an Ollama response into Isaac's format."
  [response]
  (mapv (fn [tc]
          {:type      "toolCall"
           :id        (str (java.util.UUID/randomUUID))
           :name      (get-in tc [:function :name])
           :arguments (get-in tc [:function :arguments])})
        (get-in response [:message :tool_calls])))

;; endregion ^^^^^ Public API ^^^^^

;; region ----- Tool Call Loop -----

(defn- response-token-counts [response]
  {:input-tokens  (or (:prompt_eval_count response) 0)
   :output-tokens (or (:eval_count response) 0)})

(defn- accumulate-token-counts [totals response]
  (merge-with + totals (response-token-counts response)))

(defn- build-followup-request [req response tool-calls tool-fn]
  (let [assistant-msg {:role       "assistant"
                       :content    (or (get-in response [:message :content]) "")
                       :tool_calls (get-in response [:message :tool_calls])}
        tool-results  (mapv (fn [tc]
                              {:role    "tool"
                               :content (tool-fn (:name tc) (:arguments tc))})
                            tool-calls)]
    (assoc req :messages (into (:messages req) (cons assistant-msg tool-results)))))

(defn chat-with-tools
  "Execute a chat with tool call loop.
   Returns {:response map :tool-calls [...] :token-counts {:input-tokens n :output-tokens n}}"
  [request tool-fn & [{:keys [base-url max-loops] :or {max-loops 100} :as opts}]]
  (loop [req          request
          all-tools    []
          token-counts {:input-tokens 0 :output-tokens 0}
          loops        0]
    (let [response (chat req opts)]
      (if (:error response)
        response
        (let [token-counts (accumulate-token-counts token-counts response)]
          (if (and (has-tool-calls? response) (< loops max-loops))
            (let [tool-calls (extract-tool-calls response)]
              (recur (build-followup-request req response tool-calls tool-fn)
                     (into all-tools tool-calls)
                     token-counts
                     (inc loops)))
            {:response     response
              :tool-calls   all-tools
              :token-counts token-counts}))))))

;; endregion ^^^^^ Tool Call Loop ^^^^^
