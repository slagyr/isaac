(ns isaac.llm.grover
  "Built-in test LLM provider. Grover tries his best but isn't very sharp.
   Default mode: echoes the last user message content.
   Scripted mode: consumes pre-queued responses in order.")

;; region ----- Response Queue -----

(defonce ^:private queue (atom []))

(defn enqueue! [responses]
  (swap! queue into responses))

(defn reset-queue! []
  (reset! queue []))

(defn- dequeue! []
  (let [resp (first @queue)]
    (when resp (swap! queue subvec 1))
    resp))

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
  (if (:tool_call scripted)
    (merge {:model   model
            :message {:role       "assistant"
                      :content    ""
                      :tool_calls [{:function {:name      (:tool_call scripted)
                                               :arguments (:arguments scripted)}}]}
            :done    true
            :done_reason "stop"}
           token-counts)
    (merge {:model   model
            :message {:role "assistant" :content (:content scripted)}
            :done    true
            :done_reason "stop"}
           token-counts)))

;; endregion ^^^^^ Response Building ^^^^^

;; region ----- Public API (matches ollama interface) -----

(defn chat
  "Synchronous chat. Returns a response map instantly."
  [request & [_opts]]
  (let [model    (:model request)
        scripted (dequeue!)]
    (if scripted
      (scripted-response scripted model)
      (echo-response (:messages request) model))))

(defn chat-stream
  "Streaming chat. Calls on-chunk with synthetic chunks, returns final."
  [request on-chunk & [_opts]]
  (let [response (chat request)
        content  (get-in response [:message :content])
        words    (if (seq content)
                   (clojure.string/split content #"(?<=\s)")
                   [""])]
    ;; Emit word-by-word chunks
    (doseq [w words]
      (on-chunk {:message {:role "assistant" :content w} :done false}))
    ;; Final chunk
    (let [final (assoc response :done true)]
      (on-chunk final)
      final)))

(defn chat-with-tools
  "Tool call loop. Returns {:response map :tool-calls [...] :token-counts {...}}."
  [request tool-fn & [_opts]]
  (loop [req          request
         all-tools    []
         total-input  0
         total-output 0
         loops        0]
    (let [response     (chat req)
          input        (+ total-input (:prompt_eval_count response 0))
          output       (+ total-output (:eval_count response 0))
          tool-calls   (get-in response [:message :tool_calls])]
      (if (and (seq tool-calls) (< loops 10))
        (let [isaac-tools  (mapv (fn [tc]
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
         :token-counts {:inputTokens input :outputTokens output}}))))

;; endregion ^^^^^ Public API ^^^^^
