(ns isaac.context.manager
  (:require
    [clojure.string :as str]
    [isaac.logger :as log]
    [isaac.message.content :as message-content]
    [isaac.prompt.builder :as prompt]
    [isaac.provider :as provider]
    [isaac.session.compaction :as compaction]
    [isaac.session.storage :as storage]
    [isaac.tool.builtin :as builtin]
    [isaac.tool.registry :as tool-registry]))

(defonce ^:private last-compaction-request* (atom nil))

(defn last-compaction-request []
  @last-compaction-request*)

;; region ----- Compaction -----

(defn- last-compaction [transcript]
  (->> transcript
       (filter #(= "compaction" (:type %)))
       last))

(defn- messages-from-entry-id [transcript entry-id]
  (let [keep? (atom false)]
    (->> transcript
         (keep (fn [entry]
                 (when (= (:id entry) entry-id)
                   (reset! keep? true))
                 (when (and @keep? (= "message" (:type entry)))
                   entry)))
         vec)))

(defn- messages-after-compaction [transcript compaction-id]
  (let [after? (atom false)]
    (->> transcript
         (keep (fn [entry]
                 (if (= (:id entry) compaction-id)
                   (do (reset! after? true) nil)
                   (when (and @after? (= "message" (:type entry)))
                     entry))))
         vec)))

(defn- effective-history-entries [transcript]
  (if-let [compaction (last-compaction transcript)]
    (into [compaction]
          (if-let [first-kept-id (:firstKeptEntryId compaction)]
            (messages-from-entry-id transcript first-kept-id)
            (messages-after-compaction transcript (:id compaction))))
    (->> transcript
         (filter #(= "message" (:type %)))
         vec)))

(defn- message-text [content]
  (message-content/content->text content))

(defn- ->compact-message [entry context-window]
  (if (= "compaction" (:type entry))
    {:role "user" :content (:summary entry)}
    (let [{:keys [content role]} (:message entry)
          text                  (message-text content)]
      (when (and (contains? #{"user" "assistant" "toolResult"} role)
                 (string? text)
                 (not (str/blank? text)))
        {:role    (if (= "toolResult" role) "user" role)
         :content (if (= "toolResult" role)
                    (prompt/truncate-tool-result text context-window)
                    text)}))))

(defn- tool-call-content [entry]
  (some-> entry :message message-content/first-tool-call))

(defn- tool-result-id [entry]
  (or (get-in entry [:message :toolCallId])
      (get-in entry [:message :id])))

(defn- tool-pair-message [tool-call-entry tool-result-entry context-window]
  (let [tool-call   (tool-call-content tool-call-entry)
        result-text (message-text (get-in tool-result-entry [:message :content]))]
    (when (and tool-call result-text)
      {:role    "assistant"
       :content (str "I called tool " (:name tool-call)
                      " with id " (:id tool-call)
                      " and arguments " (pr-str (:arguments tool-call))
                      ". The tool result was: " (prompt/truncate-tool-result result-text context-window))})))

(declare message-token-count)

(defn- compactables [history-entries context-window]
  (loop [remaining history-entries
          result    []]
    (if-let [entry (first remaining)]
      (if-let [tool-call (tool-call-content entry)]
        (let [next-entry (second remaining)]
          (if (and (= "message" (:type next-entry))
                   (= "toolResult" (get-in next-entry [:message :role]))
                   (= (:id tool-call) (tool-result-id next-entry)))
            (if-let [message (tool-pair-message entry next-entry context-window)]
              (recur (nnext remaining)
                     (conj result {:id      (:id entry)
                                    :ids     [(:id entry) (:id next-entry)]
                                   :entry   entry
                                   :message message
                                   :tokens  (+ (or (:tokens entry) 0)
                                               (or (:tokens next-entry) 0))}))
              (recur (nnext remaining) result))
            (recur (rest remaining) result)))
        (if-let [message (->compact-message entry context-window)]
          (recur (rest remaining)
                 (conj result {:id      (:id entry)
                               :ids     [(:id entry)]
                               :entry   entry
                               :message message
                               :tokens  (message-token-count entry message)}))
          (recur (rest remaining) result)))
      (vec result))))

(defn- message-token-count [entry message]
  (or (:tokens entry)
      (prompt/estimate-tokens {:messages [message]})))

(def ^:private memory-tool-names #{"memory_get" "memory_search" "memory_write"})

(defn- ensure-memory-tools-registered! []
  (doseq [tool-name memory-tool-names]
    (when-not (tool-registry/lookup tool-name)
      (builtin/register-all! tool-registry/register! memory-tool-names)
      (reduced nil))))

(defn- compaction-request [model provider compacted]
  {:model    model
   :messages [{:role    "system"
                :content (str "Review this conversation. Call memory_write for anything durable the user will want later. "
                              "Then produce a concise summary of what happened. Use first person ('I') for actions taken by the assistant, "
                              "refer to the user as 'the user', and preserve who asked, who acted, and who verified each step. "
                              "Output only the summary, no preamble.")}
               {:role    "user"
                :content (pr-str compacted)}]
   :tools    (prompt/build-tools-for-request (tool-registry/tool-definitions memory-tool-names) provider)})

(defn- invoke-chat-fn [chat-fn request tool-fn]
  (try
    (chat-fn request tool-fn nil)
    (catch clojure.lang.ArityException _
      (try
        (chat-fn request tool-fn)
        (catch clojure.lang.ArityException _
          (try
            (chat-fn request nil)
            (catch clojure.lang.ArityException _
              (chat-fn request))))))))

(defn- compaction-tool-fn [state-dir key-str]
  (fn [name arguments]
    (let [result (tool-registry/execute name (assoc arguments "session_key" key-str "state_dir" state-dir) memory-tool-names)]
      (if (:isError result)
        (str "Error: " (:error result))
        (:result result)))))

(defn- response-error [response]
  (or (:error response)
      (get-in response [:response :error])))

(defn- response-content [response]
  (or (get-in response [:response :message :content])
      (get-in response [:message :content])))

(defn should-compact?
  "Check if a session should trigger compaction based on token usage."
  [session-entry context-window]
  (compaction/should-compact? session-entry context-window))

(defn- chunk-budget [context-window]
  ;; Chunk against the full compaction request size, not raw message token sums.
  ;; The provider rejects based on prompt size, so use the model window directly.
  (max 1 context-window))

(defn- compactable-log-data [compactable]
  {:content-chars (count (str (get-in compactable [:message :content] "")))
   :id            (:id compactable)
   :role          (get-in compactable [:message :role])
   :tokens        (:tokens compactable)
   :type          (:type (:entry compactable))})

(defn- chunk-plan [model provider compactables context-window]
  (let [budget (chunk-budget context-window)]
    (loop [remaining compactables
            current   []
            chunks    []
            evals     []]
      (if-let [compactable (first remaining)]
        (let [candidate  (conj current compactable)
              messages   (mapv :message candidate)
              req-tokens (prompt/estimate-tokens (compaction-request model provider messages))
              eval-data  {:candidate-count          (count candidate)
                          :candidate-request-tokens req-tokens
                          :entry                    (compactable-log-data compactable)}]
          (cond
            (<= req-tokens budget)
            (recur (rest remaining) candidate chunks (conj evals (assoc eval-data :decision :append)))

            (seq current)
            (recur remaining [] (conj chunks (mapv :message current))
                   (conj evals (assoc eval-data :decision :flush-current :flushed-count (count current))))

            :else
            {:budget      budget
             :chunks      nil
             :evaluations (conj evals (assoc eval-data :decision :oversized-single))
             :failure     {:compactable             (compactable-log-data compactable)
                           :candidate-request-tokens req-tokens
                           :reason                  :oversized-single}}))
        {:budget      budget
         :chunks      (cond-> chunks
                        (seq current) (conj (mapv :message current)))
         :evaluations evals}))))

(defn- feasible-chunks [model provider compactables context-window]
  (let [plan   (chunk-plan model provider compactables context-window)
        chunks (:chunks plan)]
    (assoc plan :chunks (when (and chunks (> (count chunks) 1)) chunks))))

(defn- summarize-messages [chat-fn tool-fn model provider messages]
  (let [request (compaction-request model provider messages)]
    (reset! last-compaction-request* request)
    (invoke-chat-fn chat-fn request tool-fn)))

(defn- chunked-response [state-dir key-str chat-fn model provider chunks]
  (let [tool-fn (compaction-tool-fn state-dir key-str)]
    (log/info :session/compaction-chunked :session key-str :model model :chunks (count chunks))
    (loop [remaining chunks
           summaries  []]
      (if-let [chunk (first remaining)]
        (let [response (summarize-messages chat-fn tool-fn model provider chunk)]
          (if (response-error response)
            response
            (recur (rest remaining) (conj summaries (response-content response)))))
        (if (> (count summaries) 1)
          (summarize-messages chat-fn tool-fn model provider (mapv (fn [summary] {:role "user" :content summary}) summaries))
          {:message {:content (first summaries)}})))))

(defn compact!
  "Compact a session's conversation history into a summary.
   Sends the conversation to the LLM for summarization, then appends
   a compaction entry to the transcript.
     Options:
       :chat-fn - (fn [request opts]) to call the LLM (required)
       :transcript-lock - optional lock used only for the final transcript splice
       :compaction-llm-done - optional promise delivered after LLM call completes
       :splice-ready - optional promise waited on before performing the splice"
  [state-dir key-str {:keys [boot-files chat-fn compaction-llm-done context-window model provider soul splice-ready transcript-lock]}]
  (let [provider-name   (when provider (provider/display-name provider))
        session-entry   (storage/get-session state-dir key-str)
        transcript      (storage/get-transcript state-dir key-str)
         history-entries (effective-history-entries transcript)
         compactables    (compactables history-entries context-window)
        messages        (mapv :message compactables)
         strategy        (compaction/resolve-config session-entry context-window)
         {:keys [compact-count first-kept-entry-id tokens-before]}
         (compaction/compaction-target compactables strategy)
         compactable-head (subvec compactables 0 compact-count)
         compacted-ids   (vec (mapcat :ids compactable-head))
         compacted       (subvec messages 0 compact-count)
         _               (ensure-memory-tools-registered!)
         summary-prompt  (compaction-request model provider-name compacted)
         summary-prompt-tokens (prompt/estimate-tokens summary-prompt)
         needs-chunking? (or (> tokens-before context-window)
                             (> summary-prompt-tokens context-window))
         chunks          (when (or (> tokens-before context-window)
                                   (> summary-prompt-tokens context-window))
                           (feasible-chunks model provider-name compactable-head context-window))
         chunk-messages  (:chunks chunks)
         chunked?        (seq chunk-messages)
         chunk-request-tokens (mapv #(prompt/estimate-tokens (compaction-request model provider-name %)) chunk-messages)
         _               (log/debug :session/compaction-analysis
                                     :compact-count compact-count
                                     :compactable-count (count compactables)
                                     :compactable-head (mapv compactable-log-data compactable-head)
                                     :compactable-head-count (count compactable-head)
                                     :context-window context-window
                                     :first-kept-entry-id first-kept-entry-id
                                     :history-entry-count (count history-entries)
                                     :model model
                                     :needs-chunking needs-chunking?
                                     :session key-str
                                     :strategy strategy
                                     :summary-prompt-tokens summary-prompt-tokens
                                     :tokens-before tokens-before)
         _               (when needs-chunking?
                           (log/debug :session/compaction-chunk-plan
                                      :budget (:budget chunks)
                                      :chunk-count (count chunk-messages)
                                      :chunk-message-counts (mapv count chunk-messages)
                                      :chunk-request-tokens chunk-request-tokens
                                      :evaluations (:evaluations chunks)
                                      :failure (:failure chunks)
                                      :model model
                                      :session key-str))
         _               (when (and needs-chunking? (not chunked?))
                           (log/warn :session/compaction-chunk-infeasible
                                     :context-window context-window
                                     :failure (:failure chunks)
                                     :model model
                                     :session key-str
                                     :summary-prompt-tokens summary-prompt-tokens
                                     :tokens-before tokens-before))
         _               (reset! last-compaction-request* nil)
         response        (if chunked?
                              (chunked-response state-dir key-str chat-fn model provider-name chunk-messages)
                              (summarize-messages chat-fn (compaction-tool-fn state-dir key-str) model provider-name compacted))]
    (when compaction-llm-done
      (deliver compaction-llm-done true))
    (if (response-error response)
      response
      (let [summary           (response-content response)
            spliced-transcript (atom nil)
            splice!           (fn []
            (let [compaction-entry (storage/splice-compaction! state-dir key-str
                                                               {:summary           summary
                                                                :firstKeptEntryId  first-kept-entry-id
                                                                :tokensBefore      tokens-before
                                                                :compactedEntryIds compacted-ids})]
                                  (reset! spliced-transcript (storage/get-transcript state-dir key-str))
                                  compaction-entry))
            _                 (when splice-ready
                                (deref splice-ready 30000 nil))
            compaction-entry  (cond-> (if transcript-lock
                                        (locking transcript-lock (splice!))
                                        (splice!))
                                chunked? (assoc :chunked true))
            compacted-prompt  (prompt/build {:boot-files boot-files
                                             :model      model
                                             :soul       soul
                                             :transcript @spliced-transcript})]
        (let [new-total (:tokenEstimate compacted-prompt)]
          (storage/update-session! state-dir key-str
                                   {:last-input-tokens new-total}))
        compaction-entry))))

;; endregion ^^^^^ Compaction ^^^^^
