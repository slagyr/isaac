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

(defn- content->text [content]
  (cond
    (string? content)
    content

    (and (vector? content) (every? map? content))
    (->> content
         (filter #(= "text" (:type %)))
         (map :text)
         (apply str))

    :else
    nil))

(defn- filter-messages
  "Filter a sequence of raw message maps, removing tool calls and normalizing tool results.
   Skips tool call entries and user messages immediately before a tool call.
   Converts tool results to user messages (truncated when context-window provided)."
  [messages context-window]
  (let [msgs (vec messages)
        n    (count msgs)]
    (->> (range n)
         (keep (fn [i]
                 (let [msg      (nth msgs i)
                       next-msg (when (< (inc i) n) (nth msgs (inc i)))]
                   (cond
                     (and (= "user" (:role msg)) (some-> next-msg tool-call?))
                     nil
                     (tool-call? msg)
                     nil
                      (= "toolResult" (:role msg))
                      (let [text (content->text (:content msg))]
                        (when text
                          {:role    "user"
                           :content (if context-window
                                      (truncate-tool-result text context-window)
                                      text)}))
                      (contains? #{"user" "assistant" "error"} (:role msg))
                      (let [text (content->text (:content msg))]
                        (when text
                          {:role (:role msg) :content text}))
                      :else nil))))
          vec)))

(defn- transcript->messages
  "Extract and filter conversation messages from transcript entries."
  [transcript context-window]
  (let [messages (->> transcript
                      (filter #(= "message" (:type %)))
                      (mapv :message))]
    (filter-messages messages context-window)))

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
  [soul boot-files transcript context-window]
  (let [system-text (if boot-files
                      (str soul "\n\n" boot-files)
                      soul)
        compaction (find-last-compaction transcript)]
    (if compaction
      (let [preserved (when-let [first-kept-entry-id (:firstKeptEntryId compaction)]
                        (messages-from-entry-id transcript first-kept-entry-id))]
        (into [{:role "system" :content system-text}
               {:role "user" :content (:summary compaction)}]
              (filter-messages (if (seq preserved)
                                 preserved
                                  (messages-after-compaction transcript compaction))
                                context-window)))
      (into [{:role "system" :content system-text}]
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
      :boot-files     - optional AGENTS.md / boot file text appended to soul
      :transcript     - vector of transcript entries
      :tools          - vector of tool definitions (optional)
      :context-window - context window size for tool result truncation (optional)"
  [{:keys [boot-files model soul transcript tools context-window]}]
  (let [messages (build-messages soul boot-files transcript context-window)
        prompt   (cond-> {:model    model
                          :messages messages}
                    (seq tools) (assoc :tools (build-tools-for-request tools)))]
    (assoc prompt :tokenEstimate (estimate-tokens prompt))))

;; endregion ^^^^^ Prompt Composition ^^^^^
