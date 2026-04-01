(ns isaac.features.steps.session
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.features.matchers :as match]
    [isaac.context.manager :as ctx]
    [isaac.llm.ollama :as ollama]
    [isaac.prompt.builder :as prompt]
    [isaac.session.key :as key]
    [isaac.session.storage :as storage]))

;; region ----- Helpers -----

(defn- unquote-string [s]
  (if (and (str/starts-with? s "\"") (str/ends-with? s "\""))
    (subs s 1 (dec (count s)))
    s))

(defn- clean-dir! [path]
  (let [dir (io/file path)]
    (when (.exists dir)
      (doseq [f (reverse (file-seq dir))]
        (.delete f)))))

(defn- state-dir [] (g/get :state-dir))

(defn- current-key []
  (or (g/get :current-key)
      (:key (first (storage/list-sessions (state-dir) "main")))))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Given -----

(defgiven empty-state "an empty Isaac state directory {string}"
  [path]
  (let [dir (unquote-string path)]
    (clean-dir! dir)
    (g/assoc! :state-dir dir)))

(defgiven sessions-exist "the following sessions exist:"
  [table]
  (doseq [row (:rows table)]
    (let [row-map (zipmap (:headers table) row)
          key-str (get row-map "key")]
      (storage/create-session! (state-dir) key-str)
      (g/assoc! :current-key key-str))))

(defgiven models-exist "the following models exist:"
  [table]
  (let [models (mapv (fn [row]
                       (let [m (zipmap (:headers table) row)]
                         {:alias        (get m "alias")
                          :model        (get m "model")
                          :provider     (get m "provider")
                          :contextWindow (parse-long (get m "contextWindow"))}))
                     (:rows table))]
    (g/assoc! :models (into {} (map (fn [m] [(:alias m) m]) models)))))

(defgiven agents-exist "the following agents exist:"
  [table]
  (let [agents (mapv (fn [row]
                       (let [a (zipmap (:headers table) row)]
                         {:name  (get a "name")
                          :soul  (get a "soul")
                          :model (get a "model")}))
                     (:rows table))]
    (g/assoc! :agents (into {} (map (fn [a] [(:name a) a]) agents)))))

(defgiven agent-has-tools "the agent has tools:"
  [table]
  (let [tools (mapv (fn [row]
                      (let [t (zipmap (:headers table) row)]
                        {:name        (get t "name")
                         :description (get t "description")
                         :parameters  (json/parse-string (get t "parameters") true)}))
                    (:rows table))]
    (g/assoc! :tools tools)))

(defgiven session-compacted "the session has been compacted with summary {summary:string}"
  [summary]
  (let [key-str    (current-key)
        agent-id   (:agent (storage/parse-key key-str))
        entry      (first (filter #(= key-str (:key %))
                                  (storage/list-sessions (state-dir) agent-id)))
        transcript (storage/get-transcript (state-dir) key-str)
        last-msg   (last (filter #(= "message" (:type %)) transcript))]
    (storage/append-compaction! (state-dir) key-str
                                {:summary          (unquote-string summary)
                                 :firstKeptEntryId (:id last-msg)
                                 :tokensBefore     100})))

(defgiven exchanges-completed #"(\d+) exchanges have been completed"
  [n]
  (let [key-str (current-key)]
    (dotimes [i (parse-long n)]
      (storage/append-message! (state-dir) key-str
                               {:role "user" :content (str "Message " (inc i))})
      (storage/append-message! (state-dir) key-str
                               {:role     "assistant"
                                :content  (str "Response " (inc i))
                                :model    "qwen3-coder:30b"
                                :provider "ollama"})
      (storage/update-tokens! (state-dir) key-str
                              {:inputTokens 50 :outputTokens 30}))))

(defgiven tokens-exceed-threshold "the session totalTokens exceeds 90% of the context window"
  []
  (let [key-str  (current-key)
        models   (g/get :models)
        agents   (g/get :agents)
        agent-id (:agent (storage/parse-key key-str))
        agent    (get agents agent-id)
        model    (get models (:model agent))
        window   (:contextWindow model)
        target   (int (* 0.95 window))]
    (storage/update-tokens! (state-dir) key-str
                            {:inputTokens target :outputTokens 0})))

(defgiven large-tool-result "the session contains a tool result of {int} characters"
  [n]
  (let [n       (if (string? n) (parse-long n) n)
        key-str (current-key)
        content (apply str (repeat n "x"))]
    (storage/append-message! (state-dir) key-str
                             {:role    "assistant"
                              :content [{:type "toolCall" :id "tc-large" :name "read_file" :arguments {}}]})
    (storage/append-message! (state-dir) key-str
                             {:role       "toolResult"
                              :toolCallId "tc-large"
                              :content    content})))

;; endregion ^^^^^ Given ^^^^^

;; region ----- When: Session Creation -----

(defwhen sessions-created "the following sessions are created:"
  [table]
  (doseq [row (:rows table)]
    (let [row-map (zipmap (:headers table) row)]
      (if (get row-map "key")
        (do (storage/create-session! (state-dir) (get row-map "key"))
            (g/assoc! :current-key (get row-map "key")))
        (let [kw-map  (into {} (map (fn [[k v]] [(keyword k) v]) row-map))
              key-str (key/build-key kw-map)]
          (storage/create-session! (state-dir) key-str)
          (g/assoc! :current-key key-str))))))

(defwhen thread-sessions-created "the following thread sessions are created:"
  [table]
  (doseq [row (:rows table)]
    (let [row-map (zipmap (:headers table) row)
          key-str (key/build-thread-key (get row-map "parentKey") (get row-map "thread"))]
      (storage/create-session! (state-dir) key-str))))

;; endregion ^^^^^ When: Session Creation ^^^^^

;; region ----- When: Messages -----

(defwhen messages-appended "the following messages are appended:"
  [table]
  (let [key-str (current-key)]
    (doseq [row (:rows table)]
      (let [row-map (zipmap (:headers table) row)
            message (cond-> {:role    (get row-map "role")
                             :content (get row-map "content")}
                      (get row-map "model")    (assoc :model (get row-map "model"))
                      (get row-map "provider") (assoc :provider (get row-map "provider"))
                      (get row-map "channel")  (assoc :channel (get row-map "channel"))
                      (get row-map "to")       (assoc :to (get row-map "to")))]
        (storage/append-message! (state-dir) key-str message)))))

(defwhen tool-call-appended "an assistant message with a tool call is appended:"
  [table]
  (let [key-str (current-key)
        row-map (zipmap (:headers table) (first (:rows table)))]
    (storage/append-message! (state-dir) key-str
                             {:role    "assistant"
                              :content [{:type      "toolCall"
                                         :id        (get row-map "tool_id")
                                         :name      (get row-map "tool_name")
                                         :arguments (get row-map "arguments")}]})))

(defwhen tool-result-appended "a tool result is appended:"
  [table]
  (let [key-str (current-key)
        row-map (zipmap (:headers table) (first (:rows table)))]
    (storage/append-message! (state-dir) key-str
                             {:role       "toolResult"
                              :toolCallId (get row-map "tool_id")
                              :content    (get row-map "content")
                              :isError    (= "true" (get row-map "isError"))})))

;; endregion ^^^^^ When: Messages ^^^^^

;; region ----- When: Key Operations -----

(defwhen key-parsed "the key {string} is parsed"
  [key-str]
  (g/assoc! :parsed (key/parse-key (unquote-string key-str))))

(defwhen session-loaded "the session is loaded for key {string}"
  [key-str]
  (let [k (unquote-string key-str)]
    (g/assoc! :current-key k)))

;; endregion ^^^^^ When: Key Operations ^^^^^

;; region ----- When: Prompt Building -----

(defwhen prompt-built "a prompt is built for the session"
  []
  (let [key-str    (current-key)
        transcript (storage/get-transcript (state-dir) key-str)
        agent-id   (:agent (storage/parse-key key-str))
        agents     (g/get :agents)
        models     (g/get :models)
        agent-cfg  (get agents agent-id)
        model-cfg  (get models (:model agent-cfg))
        tools      (g/get :tools)]
    (g/assoc! :prompt (prompt/build
                        {:model      (:model model-cfg)
                         :soul       (:soul agent-cfg)
                         :transcript transcript
                         :tools      tools}))))

;; endregion ^^^^^ When: Prompt Building ^^^^^

;; region ----- When: Context Management -----

(defwhen next-user-message-sent "the next user message is sent"
  []
  (let [key-str  (current-key)
        models   (g/get :models)
        agents   (g/get :agents)
        agent-id (:agent (storage/parse-key key-str))
        agent    (get agents agent-id)
        model    (get models (:model agent))
        listing  (storage/list-sessions (state-dir) agent-id)
        entry    (first (filter #(= key-str (:key %)) listing))]
    (storage/append-message! (state-dir) key-str
                             {:role "user" :content "Continue"})
    (when (ctx/should-compact? entry (:contextWindow model))
      (let [transcript (storage/get-transcript (state-dir) key-str)
            last-id    (:id (last transcript))]
        (storage/append-compaction! (state-dir) key-str
                                    {:summary          "Mock compaction summary of the conversation."
                                     :firstKeptEntryId last-id
                                     :tokensBefore     (prompt/estimate-tokens {:messages (mapv :message (filter #(= "message" (:type %)) transcript))})})))))

(defwhen compaction-triggered "compaction is triggered"
  []
  (let [key-str    (current-key)
        transcript (storage/get-transcript (state-dir) key-str)
        last-id    (:id (last transcript))
        messages   (filter #(= "message" (:type %)) transcript)]
    (storage/append-compaction! (state-dir) key-str
                                {:summary          "Mock compaction summary of the conversation."
                                 :firstKeptEntryId last-id
                                 :tokensBefore     (prompt/estimate-tokens {:messages (mapv :message messages)})})))

;; endregion ^^^^^ When: Context Management ^^^^^

;; region ----- Given/When: LLM Interaction -----

(defn- build-prompt-for-session []
  (let [key-str    (current-key)
        transcript (storage/get-transcript (state-dir) key-str)
        agent-id   (:agent (storage/parse-key key-str))
        agents     (g/get :agents)
        models     (g/get :models)
        agent-cfg  (get agents agent-id)
        model-cfg  (get models (:model agent-cfg))
        tools      (g/get :tools)]
    (prompt/build {:model      (:model model-cfg)
                   :soul       (:soul agent-cfg)
                   :transcript transcript
                   :tools      tools})))

(defn- simple-tool-fn [name arguments]
  (str "Tool " name " called with " (pr-str arguments)))

(defgiven llm-server-down "the LLM server is not running"
  []
  (g/assoc! :ollama-base-url "http://localhost:19999"))

(defwhen prompt-sent "the prompt is sent to the LLM"
  []
  (let [p        (build-prompt-for-session)
        base-url (or (g/get :ollama-base-url) "http://localhost:11434")
        tools    (g/get :tools)
        request  (cond-> {:model    (:model p)
                          :messages (:messages p)}
                   (seq tools) (assoc :tools (prompt/build-tools-for-request tools)))
        result   (if (seq tools)
                   (ollama/chat-with-tools request simple-tool-fn {:base-url base-url})
                   (ollama/chat request {:base-url base-url}))]
    (g/assoc! :llm-result result)
    (when-not (:error result)
      (let [key-str  (current-key)
            response (if (:response result) (:response result) result)
            msg      (:message response)
            tokens   (if (:token-counts result)
                       (:token-counts result)
                       {:inputTokens  (or (:prompt_eval_count response) 0)
                        :outputTokens (or (:eval_count response) 0)})]
        ;; Append tool calls if any
        (when-let [tool-calls (:tool-calls result)]
          (doseq [tc tool-calls]
            (storage/append-message! (state-dir) key-str
                                     {:role    "assistant"
                                      :content [tc]}))
          (doseq [tc tool-calls]
            (storage/append-message! (state-dir) key-str
                                     {:role       "toolResult"
                                      :toolCallId (:id tc)
                                      :content    (simple-tool-fn (:name tc) (:arguments tc))})))
        ;; Append final assistant message
        (storage/append-message! (state-dir) key-str
                                 {:role     (:role msg)
                                  :content  (:content msg)
                                  :model    (:model response)
                                  :provider "ollama"})
        ;; Update token counts
        (storage/update-tokens! (state-dir) key-str tokens)))))

(defwhen prompt-streamed "the prompt is streamed to the LLM"
  []
  (let [p        (build-prompt-for-session)
        base-url (or (g/get :ollama-base-url) "http://localhost:11434")
        chunks   (atom [])
        request  {:model (:model p) :messages (:messages p)}
        result   (ollama/chat-stream request
                                     (fn [chunk] (swap! chunks conj chunk))
                                     {:base-url base-url})]
    (g/assoc! :llm-result result)
    (g/assoc! :stream-chunks @chunks)
    (when-not (:error result)
      (let [key-str (current-key)
            msg     (:message result)]
        (storage/append-message! (state-dir) key-str
                                 {:role     (:role msg)
                                  :content  (or (:content msg) "")
                                  :model    (:model result)
                                  :provider "ollama"})))))

(defwhen model-responds-with-tool-call "the model responds with a tool call"
  []
  ;; Narrative step — the tool call already happened in "the prompt is sent to the LLM"
  nil)

;; endregion ^^^^^ Given/When: LLM Interaction ^^^^^

;; region ----- Then -----

(defthen listing-count #"the session listing has (\d+) entr(?:y|ies)"
  [n]
  (let [agent-id (:agent (storage/parse-key (current-key)))
        listing  (storage/list-sessions (state-dir) agent-id)]
    (g/should= (parse-long n) (count listing))))

(defthen listing-matches "the session listing has entries matching:"
  [table]
  (let [agent-id (:agent (storage/parse-key (current-key)))
        listing  (storage/list-sessions (state-dir) agent-id)]
    (g/should (:pass? (match/match-entries table listing)))))

(defthen transcript-count #"the transcript has (\d+) entr(?:y|ies)"
  [n]
  (let [transcript (storage/get-transcript (state-dir) (current-key))]
    (g/should= (parse-long n) (count transcript))))

(defthen transcript-matches "the transcript has entries matching:"
  [table]
  (let [transcript (storage/get-transcript (state-dir) (current-key))
        result     (match/match-entries table transcript)]
    (g/should (:pass? result))))

(defthen parsed-key-matches "the parsed key matches:"
  [table]
  (let [parsed (g/get :parsed)
        result (match/match-object table parsed)]
    (g/should (:pass? result))))

(defthen prompt-matches "the prompt matches:"
  [table]
  (let [p      (g/get :prompt)
        result (match/match-object table p)]
    (g/should (:pass? result))))

(defthen prompt-has-token-estimate "the prompt has a token estimate greater than 0"
  []
  (let [p (g/get :prompt)]
    (g/should (> (:tokenEstimate p) 0))))

(defthen compaction-triggered-before-send "compaction is triggered before sending the prompt"
  []
  ;; Narrative assertion — compaction was triggered in the "next user message is sent" step
  ;; Verify by checking that a compaction entry exists in the transcript
  (let [transcript (storage/get-transcript (state-dir) (current-key))
        compactions (filter #(= "compaction" (:type %)) transcript)]
    (g/should (seq compactions))))

(defthen tool-result-truncated #"the tool result in the prompt is less than (\d+) characters"
  [n]
  (let [p          (g/get :prompt)
        models     (g/get :models)
        agents     (g/get :agents)
        agent-id   (:agent (storage/parse-key (current-key)))
        agent      (get agents agent-id)
        model      (get models (:model agent))
        window     (:contextWindow model)
        transcript (storage/get-transcript (state-dir) (current-key))
        tool-msgs  (filter #(= "toolResult" (get-in % [:message :role])) transcript)
        last-tool  (last tool-msgs)
        content    (get-in last-tool [:message :content])
        truncated  (ctx/truncate-tool-result content window)]
    (g/assoc! :truncated-result truncated)
    (g/should (< (count truncated) (if (string? n) (parse-long n) n)))))

(defthen tool-result-preserves-ends "the tool result preserves content at the start and end"
  []
  (let [truncated (g/get :truncated-result)]
    (g/should (str/includes? truncated "xxx"))
    (g/should (str/includes? truncated "truncated"))))

(defthen stream-chunks-incremental "response chunks arrive incrementally"
  []
  (let [chunks (g/get :stream-chunks)]
    (g/should (> (count chunks) 1))))

(defthen error-server-unreachable "an error is reported indicating the server is unreachable"
  []
  (let [result (g/get :llm-result)]
    (g/should= :connection-refused (:error result))))

(defthen transcript-no-new-entries "the transcript has no new entries after the user message"
  []
  (let [transcript (storage/get-transcript (state-dir) (current-key))
        messages   (filter #(= "message" (:type %)) transcript)]
    ;; Should only have the user message, no assistant response
    (g/should= 1 (count messages))))

;; endregion ^^^^^ Then ^^^^^
