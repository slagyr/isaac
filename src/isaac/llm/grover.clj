(ns isaac.llm.grover
  "Built-in test LLM provider. Grover tries his best but isn't very sharp.
   Default mode: echoes the last user message content.
   Scripted mode: consumes pre-queued responses in order."
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [isaac.session.bridge :as bridge]))

;; region ----- Response Queue -----

(defonce ^:private queue (atom []))
(defonce ^:private delay-enabled* (atom false))
(defonce ^:private delay-started* (atom nil))
(defonce ^:private delay-release* (atom nil))
(defonce ^:private delay-complete* (atom nil))
(defonce ^:private last-request* (atom nil))
(defonce ^:private last-provider-request* (atom nil))

(defn enqueue! [responses]
  (swap! queue into responses))

(defn reset-queue! []
  (reset! queue [])
  (reset! delay-enabled* false)
  (reset! delay-started* nil)
  (reset! delay-release* nil)
  (reset! delay-complete* nil)
  (reset! last-request* nil)
  (reset! last-provider-request* nil))

(defn enable-delay! []
  (reset! delay-enabled* true))

(defn set-delay-ms! [delay-ms]
  (reset! delay-enabled* (pos? delay-ms)))

(defn last-request []
  @last-request*)

(defn last-provider-request []
  @last-provider-request*)

(defn- dequeue! []
  (let [resp (first @queue)]
    (when resp (swap! queue subvec 1))
    resp))

(defn await-delay-start []
  (let [started @delay-started*]
    (when started
      @started)))

(defn release-delay! []
  (some-> @delay-release* (deliver true)))

(defn await-delay-complete []
  (let [complete @delay-complete*]
    (when complete
      @complete)))

(defn- maybe-delay! [session-key]
  (when @delay-enabled*
    (let [started  (promise)
          release  (promise)
          complete (promise)]
      (reset! delay-started* started)
      (reset! delay-release* release)
      (reset! delay-complete* complete)
      (deliver started true)
      (or (when (nil? session-key)
            @release)
          (loop []
            (cond
              (realized? release)
              @release

              (bridge/cancelled? session-key)
              :cancelled

              :else
              (do
                (Thread/sleep 10)
                (recur)))))
      (deliver complete true)
      (when (bridge/cancelled? session-key)
        {:error :cancelled}))))

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
      (= "exception" (:type scripted))
      (throw (Exception. (or (:content scripted) "grover exception")))

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

(defn- provider-response [body]
  (let [model    (:model body)
        scripted (dequeue!)]
    (if scripted
      (scripted-response scripted model)
      (echo-response (or (:messages body) (:input body)) model))))

(defn- capture-provider-request! [provider url headers body]
  (reset! last-provider-request* {:provider provider
                                  :url      url
                                  :headers  headers
                                  :body     body}))

(defn- chat-completions-json [response]
  {:choices [{:message {:role    "assistant"
                        :content (get-in response [:message :content])}}]
   :model   (:model response)
   :usage   {:prompt_tokens (:prompt_eval_count response)
             :completion_tokens (:eval_count response)}})

(defn- responses-json [response]
  {:output [{:type    "message"
             :role    "assistant"
             :content [{:type "output_text" :text (get-in response [:message :content])}]}]
   :model  (:model response)
   :usage  {:input_tokens (:prompt_eval_count response)
            :output_tokens (:eval_count response)}})

(defn- function-call-item [response]
  (let [tool-call (first (get-in response [:message :tool_calls]))]
    {:id   (or (:id tool-call) "fc_grover")
     :type "function_call"
     :name (get-in tool-call [:function :name])}))

(defn- function-call-arguments [response]
  (let [tool-call (first (get-in response [:message :tool_calls]))
        args      (get-in tool-call [:function :arguments])]
    (if (string? args) args (json/generate-string args))))

(defn post-json!
  [provider url headers body]
  (capture-provider-request! provider url headers body)
  (if (str/ends-with? url "/responses")
    (responses-json (provider-response body))
    (chat-completions-json (provider-response body))))

(defn- content-chunks [content]
  (cond
    (vector? content) content
    (seq content)     (str/split content #"(?<=\s)")
    :else             [""]))

(defn- reduce-provider-events [events on-chunk process-event initial]
  (reduce (fn [acc evt]
            (on-chunk evt)
            (process-event evt acc))
          initial
          events))

(defn post-sse!
  [provider url headers body on-chunk process-event initial]
  (capture-provider-request! provider url headers body)
  (let [response (provider-response body)]
    (if (:error response)
      response
      (if (str/ends-with? url "/responses")
        (let [tool-call-item (function-call-item response)
              events (if-let [_tool-call (first (get-in response [:message :tool_calls]))]
                       [{:type "response.output_item.added"
                         :item tool-call-item}
                        {:type    "response.function_call_arguments.delta"
                         :item_id (:id tool-call-item)
                         :delta   (function-call-arguments response)}
                        {:type    "response.function_call_arguments.done"
                         :item_id (:id tool-call-item)}
                        {:type     "response.completed"
                         :response {:model (:model response)
                                    :usage {:input_tokens  (:prompt_eval_count response)
                                            :output_tokens (:eval_count response)}}}]
                       (concat (map (fn [chunk]
                                      {:type "response.output_text.delta"
                                       :delta chunk})
                                    (content-chunks (get-in response [:message :content])))
                               [{:type     "response.completed"
                                 :response {:model (:model response)
                                            :usage {:input_tokens  (:prompt_eval_count response)
                                                    :output_tokens (:eval_count response)}}}]))]
          (reduce-provider-events events on-chunk process-event initial))
        (let [events (concat (map (fn [chunk]
                                    {:model   (:model response)
                                     :choices [{:delta {:content chunk}}]})
                                  (content-chunks (get-in response [:message :content])))
                             [{:usage   {:prompt_tokens     (:prompt_eval_count response)
                                         :completion_tokens (:eval_count response)}
                               :choices [{:delta {}}]}])]
          (reduce-provider-events events on-chunk process-event initial))))))

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
  (let [session-key (get-in opts [:provider-config :session-key])
        delayed?    @delay-enabled*
        delay-error (when delayed?
                      (maybe-delay! session-key))]
    (or delay-error
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
