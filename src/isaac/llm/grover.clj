(ns isaac.llm.grover
  "Built-in test LLM provider. Grover tries his best but isn't very sharp.
   Default mode: echoes the last user message content.
   Scripted mode: consumes pre-queued responses in order."
  (:require
    [clojure.string :as str]
    [isaac.session.bridge :as bridge]))

;; region ----- Response Queue -----

(defonce ^:private queue (atom []))
(defonce ^:private delay-ms* (atom 0))
(defonce ^:private last-request* (atom nil))

(defn enqueue! [responses]
  (swap! queue into responses))

(defn reset-queue! []
  (reset! queue [])
  (reset! delay-ms* 0)
  (reset! last-request* nil))

(defn set-delay-ms! [delay-ms]
  (reset! delay-ms* delay-ms))

(defn last-request []
  @last-request*)

(defn- dequeue! []
  (let [resp (first @queue)]
    (when resp (swap! queue subvec 1))
    resp))

(defn- maybe-delay! [session-key]
  (loop [remaining @delay-ms*]
    (cond
      (bridge/cancelled? session-key) {:error :cancelled}
      (<= remaining 0)                nil
      :else                           (do
                                        (Thread/sleep (min 50 remaining))
                                        (recur (- remaining 50))))))

;; endregion ^^^^^ Response Queue ^^^^^

;; region ----- Response Building -----

(def ^:private token-counts {:prompt_eval_count 25 :eval_count 12})

(defn- echo-response [messages model]
  (let [last-user (->> messages
                       (filter #(= "user" (:role %)))
                       last
                       :content)]
    (merge {:model   model
            :message {:role "assistant" :content (or last-user "...")}
            :done    true
            :done_reason "stop"}
           token-counts)))

(defn- scripted-response [scripted model]
  (let [resp-model (if (contains? scripted :model) (:model scripted) model)]
    (cond
      (= "error" (:type scripted))
      {:error :llm-error :message (:content scripted) :model resp-model}

      (:tool_call scripted)
      (merge {:model   resp-model
              :message {:role       "assistant"
                        :content    ""
                        :tool_calls [{:function {:name      (:tool_call scripted)
                                                 :arguments (:arguments scripted)}}]}
              :done    true
              :done_reason "stop"}
             token-counts)

      :else
      (merge {:model   resp-model
              :message {:role "assistant" :content (:content scripted)}
              :done    true
              :done_reason "stop"}
             token-counts))))

;; endregion ^^^^^ Response Building ^^^^^

;; region ----- Public API (matches ollama interface) -----

(defn- boolean-option [value default]
  (cond
    (nil? value)     default
    (boolean? value) value
    (string? value)  (not (#{"false" "0" "no" "off"} (str/lower-case value)))
    :else            (boolean value)))

(defn- stream-supports-tool-calls? [opts]
  (let [raw-value (or (:streamSupportsToolCalls opts)
                      (get-in opts [:provider-config :streamSupportsToolCalls]))]
    (boolean-option raw-value true)))

(defn chat
  "Synchronous chat. Returns a response map instantly."
  [request & [opts]]
  (reset! last-request* request)
  (let [session-key (get-in opts [:provider-config :session-key])]
    (or (maybe-delay! session-key)
        (let [model    (:model request)
              scripted (dequeue!)]
          (if scripted
            (scripted-response scripted model)
            (echo-response (:messages request) model))))))

(defn chat-stream
  "Streaming chat. Calls on-chunk with synthetic chunks, returns final."
  [request on-chunk & [opts]]
  (let [response (chat request opts)]
    (if (:error response)
      response
      (let [supports-tool-calls? (stream-supports-tool-calls? opts)
            content              (get-in response [:message :content])
            words                (cond
                                   (vector? content) content
                                   (seq content)     (str/split content #"(?<=\s)")
                                   :else             [""])]
        ;; Emit word-by-word chunks
        (doseq [w words]
          (on-chunk {:message {:role "assistant" :content w} :done false}))
        ;; Final chunk
        (let [final-content (if (vector? content) (apply str content) content)
              final         (cond-> (-> response
                                        (assoc-in [:message :content] final-content)
                                        (assoc :done true))
                              (not supports-tool-calls?) (update :message dissoc :tool_calls))]
          (on-chunk final)
          final)))))

(defn chat-with-tools
  "Tool call loop. Returns {:response map :tool-calls [...] :token-counts {...}}."
  [request tool-fn & [_opts]]
  (loop [req          request
          all-tools    []
          total-input  0
          total-output 0
          loops        0]
    (let [response     (chat req _opts)
          input        (+ total-input (:prompt_eval_count response 0))
          output       (+ total-output (:eval_count response 0))
          tool-calls   (get-in response [:message :tool_calls])]
      (if (:error response)
        response
        (if (and (seq tool-calls) (< loops 10))
          (let [isaac-tools   (mapv (fn [tc]
                                      {:type      "toolCall"
                                       :id        (str (random-uuid))
                                       :name      (get-in tc [:function :name])
                                       :arguments (get-in tc [:function :arguments])})
                                    tool-calls)
                assistant-msg {:role       "assistant"
                               :content    (get-in response [:message :content])
                               :tool_calls tool-calls}
                tool-results  (mapv (fn [tc]
                                      {:role    "tool"
                                       :content (tool-fn (:name tc) (:arguments tc))})
                                    isaac-tools)
                new-messages  (into (:messages req) (cons assistant-msg tool-results))]
            (recur (assoc req :messages new-messages)
                   (into all-tools isaac-tools)
                   input output (inc loops)))
          {:response     response
           :tool-calls   all-tools
           :token-counts {:inputTokens input :outputTokens output}})))))

;; endregion ^^^^^ Public API ^^^^^
