(ns isaac.context.manager
  (:require
    [clojure.string :as str]
    [isaac.prompt.builder :as prompt]
    [isaac.session.compaction :as compaction]
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
    (let [{:keys [content role]} (:message entry)
          text                  (cond
                                  (string? content)
                                  content

                                  (and (vector? content) (every? map? content))
                                  (->> content
                                       (filter #(= "text" (:type %)))
                                       (map :text)
                                       (apply str))

                                  :else
                                  nil)]
      (when (and (contains? #{"user" "assistant"} role) (string? text) (not (str/blank? text)))
        {:role role :content text}))))

(defn- message-token-count [entry message]
  (or (:tokens entry)
      (prompt/estimate-tokens {:messages [message]})))

(defn should-compact?
  "Check if a session should trigger compaction based on token usage."
  [session-entry context-window]
  (compaction/should-compact? session-entry context-window))

(defn compact!
  "Compact a session's conversation history into a summary.
   Sends the conversation to the LLM for summarization, then appends
    a compaction entry to the transcript.
    Options:
       :chat-fn - (fn [request opts]) to call the LLM (required)"
  [state-dir key-str {:keys [boot-files chat-fn context-window model soul]}]
  (let [session-entry   (storage/get-session state-dir key-str)
        transcript      (storage/get-transcript state-dir key-str)
        history-entries (effective-history-entries transcript)
        compactables    (->> history-entries
                             (keep (fn [entry]
                                     (when-let [message (->compact-message entry)]
                                       {:id      (:id entry)
                                        :entry   entry
                                        :message message
                                        :tokens  (message-token-count entry message)})))
                             vec)
        messages        (mapv :message compactables)
        strategy        (compaction/resolve-config session-entry context-window)
        {:keys [compact-count first-kept-entry-id tokens-before]}
        (compaction/compaction-target compactables strategy)
        compacted       (subvec messages 0 compact-count)
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
                                                          :firstKeptEntryId first-kept-entry-id
                                                          :tokensBefore     tokens-before})
            _                (storage/truncate-after-compaction! state-dir key-str)
            compacted-prompt (prompt/build {:boot-files boot-files
                                            :model      model
                                            :soul       soul
                                            :transcript (conj transcript compaction-entry)})]
        (let [new-total (:tokenEstimate compacted-prompt)]
          (storage/update-session! state-dir key-str
                                   {:inputTokens  new-total
                                    :outputTokens 0
                                    :totalTokens  new-total}))
        compaction-entry))))

;; endregion ^^^^^ Compaction ^^^^^
