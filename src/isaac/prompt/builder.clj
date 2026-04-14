(ns isaac.prompt.builder
  (:require [cheshire.core :as json]
            [clojure.string :as str]))

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

(defn- extract-tool-calls-from-msg
  "Return a seq of tool-call maps from a message, or nil if the message is not a tool call.
   Handles three storage formats:
     1. type at message level  {:role \"assistant\" :type \"toolCall\" :id ...}
     2. content as vector      {:role \"assistant\" :content [{:type \"toolCall\" ...}]}
     3. content as JSON string {:role \"assistant\" :content \"[{\\\"type\\\":\\\"toolCall\\\",...}]\"}"
  [msg]
  (cond
    (= "toolCall" (:type msg))
    [{:type "toolCall" :id (:id msg) :name (:name msg) :arguments (:arguments msg)}]

    (and (vector? (:content msg))
         (= "toolCall" (:type (first (:content msg)))))
    (:content msg)

    (and (string? (:content msg)) (str/starts-with? (:content msg) "["))
    (try
      (let [parsed (json/parse-string (:content msg) true)]
        (when (and (sequential? parsed) (= "toolCall" (:type (first parsed))))
          (vec parsed)))
      (catch Exception _ nil))

    :else nil))

(defn- tool-call? [msg]
  (some? (extract-tool-calls-from-msg msg)))

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
  "Filter a sequence of raw message maps for Ollama-compatible providers.
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
                      (contains? #{"user" "assistant"} (:role msg))
                       (let [text (content->text (:content msg))]
                         (when text
                           {:role (:role msg) :content text}))
                      :else nil))))
          vec)))

(defn- format-tool-call-for-openai [tc]
  {:type     "function"
   :id       (:id tc)
   :function {:name      (:name tc)
              :arguments (json/generate-string (:arguments tc))}})

(defn- filter-messages-openai
  "Filter messages for OpenAI-compatible providers.
   Preserves full tool call chain with proper types:
     assistant messages with tool_calls array, tool results as role=tool."
  [messages context-window]
  (let [msgs (vec messages)
        n    (count msgs)]
    (->> (range n)
         (keep (fn [i]
                 (let [msg      (nth msgs i)
                       prev-msg (when (pos? i) (nth msgs (dec i)))]
                   (cond
                     (tool-call? msg)
                     (let [tool-calls (extract-tool-calls-from-msg msg)]
                       {:role       "assistant"
                        :tool_calls (mapv format-tool-call-for-openai tool-calls)})

                     (= "toolResult" (:role msg))
                     (let [text    (or (content->text (:content msg)) (str (:content msg)))
                           tc-id   (or (:toolCallId msg)
                                       (:id msg)
                                       (when-let [tcs (and prev-msg (extract-tool-calls-from-msg prev-msg))]
                                         (:id (first tcs))))]
                       (when text
                         {:role         "tool"
                          :tool_call_id tc-id
                          :content      (if context-window
                                          (truncate-tool-result text context-window)
                                          text)}))

                     (contains? #{"user" "assistant"} (:role msg))
                     (when-let [text (content->text (:content msg))]
                       {:role (:role msg) :content text})

                     :else nil))))
         (remove nil?)
         vec)))

(defn- transcript->messages
  "Extract and filter conversation messages from transcript entries."
  [transcript context-window filter-fn]
  (let [messages (->> transcript
                      (filter #(= "message" (:type %)))
                      (mapv :message))]
    (filter-fn messages context-window)))

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
  [soul boot-files transcript context-window provider]
  (let [system-text (if boot-files
                      (str soul "\n\n" boot-files)
                      soul)
        filter-fn   (if (= "openai" provider) filter-messages-openai filter-messages)
        compaction  (find-last-compaction transcript)]
    (if compaction
      (let [preserved (when-let [first-kept-entry-id (:firstKeptEntryId compaction)]
                        (messages-from-entry-id transcript first-kept-entry-id))]
        (into [{:role "system" :content system-text}
               {:role "user" :content (:summary compaction)}]
              (filter-fn (if (seq preserved)
                           preserved
                           (messages-after-compaction transcript compaction))
                         context-window)))
      (into [{:role "system" :content system-text}]
            (transcript->messages transcript context-window filter-fn)))))

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
  "Build a prompt request compatible with the target provider.
   Options:
     :model          - resolved model string (e.g. \"qwen3-coder:30b\")
     :soul           - system prompt text
     :boot-files     - optional AGENTS.md / boot file text appended to soul
     :transcript     - vector of transcript entries
     :tools          - vector of tool definitions (optional)
     :context-window - context window size for tool result truncation (optional)
     :provider       - provider name; \"openai\" enables full tool call chain format"
  [{:keys [boot-files model soul transcript tools context-window provider]}]
  (let [messages (build-messages soul boot-files transcript context-window provider)
        prompt   (cond-> {:model    model
                          :messages messages}
                    (seq tools) (assoc :tools (build-tools-for-request tools)))]
    (assoc prompt :tokenEstimate (estimate-tokens prompt))))

;; endregion ^^^^^ Prompt Composition ^^^^^
