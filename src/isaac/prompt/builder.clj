(ns isaac.prompt.builder)

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

;; region ----- History Extraction -----

(defn- tool-call? [msg]
  (or (= "toolCall" (:type msg))
      (and (vector? (:content msg))
           (= "toolCall" (:type (first (:content msg)))))))

(defn- transcript->messages
  "Extract conversation messages from transcript entries.
   Skips tool call entries and user messages immediately before a tool call.
   Includes tool results as user messages (truncated when context-window provided)."
  [transcript context-window]
  (let [messages (->> transcript
                      (filter #(= "message" (:type %)))
                      (mapv :message))
        n        (count messages)]
    (->> (range n)
         (keep (fn [i]
                 (let [msg      (nth messages i)
                       next-msg (when (< (inc i) n) (nth messages (inc i)))]
                   (cond
                     (and (= "user" (:role msg)) (some-> next-msg tool-call?))
                     nil
                     (tool-call? msg)
                     nil
                     (= "toolResult" (:role msg))
                     {:role    "user"
                      :content (if context-window
                                 (truncate-tool-result (:content msg) context-window)
                                 (:content msg))}
                     (and (contains? #{"user" "assistant"} (:role msg)) (string? (:content msg)))
                     {:role (:role msg) :content (:content msg)}
                     :else nil))))
         vec)))

(defn- find-last-compaction
  "Find the last compaction entry in the transcript, if any."
  [transcript]
  (->> transcript
       (filter #(= "compaction" (:type %)))
       last))

(defn- messages-after-compaction
  "Get messages that appear after the compaction entry in the transcript."
  [transcript compaction]
  (let [compaction-id (:id compaction)
        after?        (atom false)]
    (->> transcript
         (filter (fn [entry]
                   (if (= (:id entry) compaction-id)
                     (do (reset! after? true) false)
                     (and @after? (= "message" (:type entry))))))
         (mapv :message))))

(defn- messages-from-entry-id
  "Get messages from the first preserved message onward, regardless of compaction position."
  [transcript entry-id]
  (let [keep? (atom false)]
    (->> transcript
         (keep (fn [entry]
                 (when (= (:id entry) entry-id)
                   (reset! keep? true))
                 (when (and @keep? (= "message" (:type entry)))
                   (:message entry))))
         vec)))

;; endregion ^^^^^ History Extraction ^^^^^

;; region ----- Prompt Composition -----

(defn- build-messages
  "Compose the messages array: system prompt + history (or compacted summary + post-compaction)."
  [soul transcript context-window]
  (let [compaction (find-last-compaction transcript)]
    (if compaction
      (let [preserved (when-let [first-kept-entry-id (:firstKeptEntryId compaction)]
                        (messages-from-entry-id transcript first-kept-entry-id))]
        (into [{:role "system" :content soul}
               {:role "user" :content (:summary compaction)}]
              (if (seq preserved)
                preserved
                (messages-after-compaction transcript compaction))))
      (into [{:role "system" :content soul}]
            (transcript->messages transcript context-window)))))

(defn build-tools-for-request
  "Format tool definitions for the Ollama API."
  [tools]
  (when (seq tools)
    (mapv (fn [tool]
            {:type     "function"
             :function {:name        (:name tool)
                        :description (:description tool)
                        :parameters  (:parameters tool)}})
          tools)))

(defn estimate-tokens
  "Estimate token count using chars/4 heuristic."
  [prompt]
  (let [text (str prompt)]
    (max 1 (quot (count text) 4))))

(defn build
  "Build an Ollama-compatible prompt request.
   Options:
     :model          - resolved model string (e.g. \"qwen3-coder:30b\")
     :soul           - system prompt text
     :transcript     - vector of transcript entries
     :tools          - vector of tool definitions (optional)
     :context-window - context window size for tool result truncation (optional)"
  [{:keys [model soul transcript tools context-window]}]
  (let [messages (build-messages soul transcript context-window)
        prompt   (cond-> {:model    model
                          :messages messages}
                   (seq tools) (assoc :tools (build-tools-for-request tools)))]
    (assoc prompt :tokenEstimate (estimate-tokens prompt))))

;; endregion ^^^^^ Prompt Composition ^^^^^
