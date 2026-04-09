(ns isaac.context.manager
  (:require
    [isaac.prompt.builder :as prompt]
    [isaac.session.storage :as storage]))

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

(defn- ->compact-message [entry]
  (if (= "compaction" (:type entry))
    {:role "user" :content (:summary entry)}
    (let [{:keys [content role]} (:message entry)]
      (when (and (contains? #{"user" "assistant"} role) (string? content))
        {:role role :content content}))))

(defn- first-kept-entry-id [compactables compact-count]
  (let [remaining (subvec compactables compact-count)
        next-user (first (filter #(= "user" (get-in % [:message :role])) remaining))]
    (:id (:entry (or next-user (first remaining))))))

(defn should-compact?
  "Check if a session should trigger compaction based on token usage."
  [session-entry context-window]
  (let [total  (:totalTokens session-entry 0)
        thresh (* 0.9 context-window)]
    (>= total thresh)))

(defn compact!
  "Compact a session's conversation history into a summary.
   Sends the conversation to the LLM for summarization, then appends
    a compaction entry to the transcript.
    Options:
       :chat-fn - (fn [request opts]) to call the LLM (required)"
  [state-dir key-str {:keys [model soul context-window chat-fn]}]
  (let [transcript      (storage/get-transcript state-dir key-str)
        history-entries (effective-history-entries transcript)
        compactables    (->> history-entries
                             (keep (fn [entry]
                                     (when-let [message (->compact-message entry)]
                                       {:entry entry :message message})))
                             vec)
        messages        (mapv :message compactables)
        tokens-before   (prompt/estimate-tokens {:messages messages})
        compact-count   (if (> tokens-before context-window)
                          (loop [n 1]
                            (if (> n (count messages))
                              (count messages)
                              (if (<= (prompt/estimate-tokens {:messages (subvec messages 0 n)}) context-window)
                                (recur (inc n))
                                (max 1 (dec n)))))
                          (count messages))
        compacted       (subvec messages 0 compact-count)
        first-kept-id   (first-kept-entry-id compactables compact-count)
        summary-prompt {:model    model
                        :messages [{:role    "system"
                                    :content "Summarize the following conversation concisely. Focus on key decisions, facts established, and the current state of the discussion. Output only the summary, no preamble."}
                                   {:role "user"
                                      :content (pr-str compacted)}]}
        response       (try
                         (chat-fn summary-prompt nil)
                         (catch clojure.lang.ArityException _
                           (chat-fn summary-prompt)))]
    (if (:error response)
      response
      (let [summary          (get-in response [:message :content])
            compaction-entry (storage/append-compaction! state-dir key-str
                                                        {:summary          summary
                                                         :firstKeptEntryId first-kept-id
                                                         :tokensBefore     tokens-before})
            compacted-prompt (prompt/build {:model      model
                                            :soul       soul
                                            :transcript (conj transcript compaction-entry)})]
        (let [new-total (:tokenEstimate compacted-prompt)]
          (storage/update-session! state-dir key-str
                                   {:inputTokens  new-total
                                    :outputTokens 0
                                    :totalTokens  new-total}))
        compaction-entry))))

;; endregion ^^^^^ Compaction ^^^^^

;; region ----- Tool Result Truncation -----

(defn truncate-tool-result
  "Truncate a tool result string using head-and-tail strategy.
   max-chars defaults to 30% of context-window * 4 (chars per token estimate)."
  [content context-window]
  (let [max-chars (int (* 0.3 context-window 4))
        len       (count content)]
    (if (<= len max-chars)
      content
      (let [half      (quot max-chars 2)
            head      (subs content 0 half)
            tail      (subs content (- len half))
            truncated (str head "\n\n... [" (- len max-chars) " characters truncated] ...\n\n" tail)]
        truncated))))

;; endregion ^^^^^ Tool Result Truncation ^^^^^
