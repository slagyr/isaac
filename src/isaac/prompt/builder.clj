(ns isaac.prompt.builder)

;; region ----- History Extraction -----

(defn- ->prompt-message [{:keys [content role]}]
  (when (and (contains? #{"user" "assistant"} role) (string? content))
    {:role role :content content}))

(defn- transcript->messages
  "Extract conversation messages from transcript entries.
   Skips session headers and tool artifact entries."
  [transcript]
  (->> transcript
       (filter #(= "message" (:type %)))
       (map :message)
       (keep ->prompt-message)
       vec))

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
  [soul transcript]
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
            (transcript->messages transcript)))))

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
     :model      - resolved model string (e.g. \"qwen3-coder:30b\")
     :soul       - system prompt text
     :transcript - vector of transcript entries
     :tools      - vector of tool definitions (optional)"
  [{:keys [model soul transcript tools]}]
  (let [messages (build-messages soul transcript)
        prompt   (cond-> {:model    model
                          :messages messages}
                   (seq tools) (assoc :tools (build-tools-for-request tools)))]
    (assoc prompt :tokenEstimate (estimate-tokens prompt))))

;; endregion ^^^^^ Prompt Composition ^^^^^
