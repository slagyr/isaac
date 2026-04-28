(ns isaac.context.manager
  (:require
    [clojure.string :as str]
    [isaac.prompt.builder :as prompt]
    [isaac.session.compaction :as compaction]
    [isaac.session.storage :as storage]
    [isaac.tool.builtin :as builtin]
    [isaac.tool.registry :as tool-registry]))

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

(def ^:private memory-tool-names #{"memory_get" "memory_search" "memory_write"})

(defn- ensure-memory-tools-registered! []
  (doseq [tool-name memory-tool-names]
    (when-not (tool-registry/lookup tool-name)
      (builtin/register-all! tool-registry/register! memory-tool-names)
      (reduced nil))))

(defn- compaction-request [model compacted]
  {:model    model
   :messages [{:role    "system"
               :content (str "Review this conversation. Call memory_write for anything durable the user will want later. "
                             "Then produce a concise summary of what happened. Output only the summary, no preamble.")}
              {:role    "user"
               :content (pr-str compacted)}]
   :tools    (tool-registry/tool-definitions memory-tool-names)})

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
    (let [result (tool-registry/execute name (assoc arguments :session-key key-str :state-dir state-dir) memory-tool-names)]
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

(defn compact!
  "Compact a session's conversation history into a summary.
   Sends the conversation to the LLM for summarization, then appends
   a compaction entry to the transcript.
     Options:
       :chat-fn - (fn [request opts]) to call the LLM (required)
       :transcript-lock - optional lock used only for the final transcript splice
       :compaction-llm-done - optional promise delivered after LLM call completes
       :splice-ready - optional promise waited on before performing the splice"
  [state-dir key-str {:keys [boot-files chat-fn compaction-llm-done context-window model soul splice-ready transcript-lock]}]
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
         compacted-ids   (mapv :id (subvec compactables 0 compact-count))
         compacted       (subvec messages 0 compact-count)
         _               (ensure-memory-tools-registered!)
         summary-prompt  (compaction-request model compacted)
         response        (invoke-chat-fn chat-fn summary-prompt (compaction-tool-fn state-dir key-str))]
    (when compaction-llm-done
      (deliver compaction-llm-done true))
    (if (response-error response)
      response
      (let [summary           (response-content response)
            spliced-transcript (atom nil)
            splice!           (fn []
                                (let [compaction-entry (storage/splice-compaction! state-dir key-str
                                                                                   {:summary          summary
                                                                                    :firstKeptEntryId first-kept-entry-id
                                                                                    :tokensBefore     tokens-before
                                                                                    :compactedEntryIds compacted-ids})]
                                  (reset! spliced-transcript (storage/get-transcript state-dir key-str))
                                  compaction-entry))
            _                 (when splice-ready
                                (deref splice-ready 30000 nil))
            compaction-entry  (if transcript-lock
                                (locking transcript-lock (splice!))
                                (splice!))
            compacted-prompt  (prompt/build {:boot-files boot-files
                                             :model      model
                                             :soul       soul
                                             :transcript @spliced-transcript})]
        (let [new-total (:tokenEstimate compacted-prompt)]
          (storage/update-session! state-dir key-str
                                   {:input-tokens  new-total
                                    :output-tokens 0
                                    :total-tokens  new-total}))
        compaction-entry))))

;; endregion ^^^^^ Compaction ^^^^^
