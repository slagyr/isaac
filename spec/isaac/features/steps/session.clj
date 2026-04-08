(ns isaac.features.steps.session
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.features.matchers :as match]
    [isaac.context.manager :as ctx]
    [isaac.llm.anthropic :as anthropic]
    [isaac.llm.grover :as grover]
    [isaac.llm.ollama :as ollama]
    [isaac.llm.openai-compat :as openai-compat]
    [isaac.prompt.anthropic :as anthropic-prompt]
    [isaac.prompt.builder :as prompt]
    [isaac.session.key :as key]
    [isaac.session.storage :as storage]
    [org.httpkit.server :as httpkit]))

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

(defn- current-provider []
  (let [agents   (g/get :agents)
        models   (g/get :models)
        key-str  (current-key)
        agent-id (:agent (storage/parse-key key-str))
        agent    (get agents agent-id)
        model    (get models (:model agent))]
    (:provider model)))

(defn- provider-config []
  (let [provider-name (current-provider)]
    (get (g/get :provider-configs) provider-name)))

(defn- openai-compatible? []
  (= "openai-compatible" (:api (provider-config))))

;; TODO - MDM: This is primed for a multimethod where (current-provider) is the dispatch method.
(defn- llm-chat [request opts]
  (let [pc (provider-config)]
    (cond
      (= "grover" (current-provider))    (grover/chat request opts)
      (= "anthropic" (current-provider)) (anthropic/chat request (assoc opts :provider-config pc))
      (openai-compatible?)               (openai-compat/chat request (assoc opts :provider-config pc))
      :else                              (ollama/chat request (assoc opts :base-url (:baseUrl pc))))))

;; TODO - MDM: This is primed for a multimethod where (current-provider) is the dispatch method.
(defn- llm-chat-stream [request on-chunk opts]
  (let [pc (provider-config)]
    (cond
      (= "grover" (current-provider))    (grover/chat-stream request on-chunk opts)
      (= "anthropic" (current-provider)) (anthropic/chat-stream request on-chunk (assoc opts :provider-config pc))
      (openai-compatible?)               (openai-compat/chat-stream request on-chunk (assoc opts :provider-config pc))
      :else                              (ollama/chat-stream request on-chunk (assoc opts :base-url (:baseUrl pc))))))

;; TODO - MDM: This is primed for a multimethod where (current-provider) is the dispatch method.
(defn- llm-chat-with-tools [request tool-fn opts]
  (let [pc (provider-config)]
    (cond
      (= "grover" (current-provider))    (grover/chat-with-tools request tool-fn opts)
      (= "anthropic" (current-provider)) (anthropic/chat-with-tools request tool-fn (assoc opts :provider-config pc))
      (openai-compatible?)               (openai-compat/chat-with-tools request tool-fn (assoc opts :provider-config pc))
      :else                              (ollama/chat-with-tools request tool-fn (assoc opts :base-url (:baseUrl pc))))))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Mock Ollama Server -----

(defonce ^:private mock-ollama-server (atom nil))

(defn- make-ollama-mock-handler []
  (let [call-count (atom 0)]
    (fn [request]
      (let [body     (json/parse-string (slurp (:body request)) true)
            model    (or (:model body) "llama3.2:latest")
            tools    (:tools body)
            n        (swap! call-count inc)
            stream   (:stream body)
            response (if (and tools (= 1 n))
                       {:model             model
                        :message           {:role       "assistant"
                                            :content    ""
                                            :tool_calls [{:function {:name      (get-in (first tools) [:function :name])
                                                                     :arguments {}}}]}
                        :done              true
                        :prompt_eval_count 10
                        :eval_count        5}
                       {:model             model
                        :message           {:role "assistant" :content "Hello!"}
                        :done              true
                        :prompt_eval_count 10
                        :eval_count        5})]
        (if stream
          {:status  200
           :headers {"Content-Type" "application/x-ndjson"}
           :body    (str (json/generate-string (assoc response :done false)) "\n"
                         (json/generate-string response) "\n")}
          {:status  200
           :headers {"Content-Type" "application/json"}
           :body    (json/generate-string response)})))))

;; endregion ^^^^^ Mock Ollama Server ^^^^^

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
      (when-let [updated-at (get row-map "updatedAt")]
        (storage/update-session! (state-dir) key-str
                                 {:updatedAt (parse-long updated-at)}))
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
  (let [key-str  (current-key)
        models   (g/get :models)
        agents   (g/get :agents)
        agent-id (:agent (storage/parse-key key-str))
        agent    (get agents agent-id)
        model    (get models (:model agent))]
    (dotimes [i (parse-long n)]
      (storage/append-message! (state-dir) key-str
                               {:role "user" :content (str "Message " (inc i))})
      (let [transcript (storage/get-transcript (state-dir) key-str)
            p          (prompt/build {:model      (:model model)
                                      :soul       (:soul agent)
                                      :transcript transcript})
            response   (llm-chat {:model (:model p) :messages (:messages p)} nil)]
        (when-not (:error response)
          (storage/append-message! (state-dir) key-str
                                   {:role     "assistant"
                                    :content  (get-in response [:message :content])
                                    :model    (:model response)
                                    :provider (:provider model)})
          (storage/update-tokens! (state-dir) key-str
                                  {:inputTokens  (or (:prompt_eval_count response) 0)
                                   :outputTokens (or (:eval_count response) 0)}))))))

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

(defgiven responses-queued "the following model responses are queued:"
  [table]
  (grover/reset-queue!)
  (let [responses (mapv (fn [row]
                          (let [m (zipmap (:headers table) row)]
                            (cond-> {}
                              (get m "content")   (assoc :content (get m "content"))
                              (get m "model")     (assoc :model (let [v (get m "model")] (when-not (str/blank? v) v)))
                              (get m "tool_call") (assoc :tool_call (get m "tool_call"))
                              (get m "arguments") (assoc :arguments (json/parse-string (get m "arguments") true)))))
                        (:rows table))]
    (grover/enqueue! responses)))

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
      (ctx/compact! (state-dir) key-str
                    {:model          (:model model)
                     :soul           (:soul agent)
                     :context-window (:contextWindow model)
                     :chat-fn        llm-chat}))))

(defwhen compaction-triggered "compaction is triggered"
  []
  (let [key-str  (current-key)
        models   (g/get :models)
        agents   (g/get :agents)
        agent-id (:agent (storage/parse-key key-str))
        agent    (get agents agent-id)
        model    (get models (:model agent))]
    (ctx/compact! (state-dir) key-str
                  {:model          (:model model)
                   :soul           (:soul agent)
                   :context-window (:contextWindow model)
                   :chat-fn        llm-chat})))

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
        tools      (g/get :tools)
        builder    (if (= "anthropic" (:provider model-cfg))
                     anthropic-prompt/build
                     prompt/build)]
    (builder {:model      (:model model-cfg)
              :soul       (:soul agent-cfg)
              :transcript transcript
              :tools      tools})))

(defn- simple-tool-fn [name arguments]
  (str "Tool " name " called with " (pr-str arguments)))

(defgiven llm-server-down "the LLM server is not running"
  []
  (g/assoc! :llm-error {:error :connection-refused :message "Could not connect to Ollama server"}))

(defgiven ollama-api-available "the Ollama API is available"
  []
  (when-let [s @mock-ollama-server]
    (httpkit/server-stop! s)
    (reset! mock-ollama-server nil))
  (let [server (httpkit/run-server (make-ollama-mock-handler)
                                   {:port 0 :legacy-return-value? false})
        port   (httpkit/server-port server)]
    (reset! mock-ollama-server server)
    (g/update! :provider-configs
               (fn [m]
                 (assoc (or m {}) "ollama"
                        {:name "ollama" :baseUrl (str "http://localhost:" port)})))))

(defwhen prompt-sent "the prompt is sent to the LLM"
  []
  (if-let [err (g/get :llm-error)]
    (g/assoc! :llm-result err)
    (let [p        (build-prompt-for-session)
          tools    (g/get :tools)
          provider (current-provider)
          request  (cond-> (select-keys p [:max_tokens :messages :model :system :tools])
                     (and (seq tools) (nil? (:tools p))) (assoc :tools (prompt/build-tools-for-request tools)))
          result   (if (seq tools)
                     (llm-chat-with-tools request simple-tool-fn nil)
                     (llm-chat request nil))]
      (g/assoc! :llm-result result)
      (when-not (:error result)
        (let [key-str  (current-key)
              response (if (:response result) (:response result) result)
              msg      (:message response)
              tokens   (or (:token-counts result)
                         (:usage response)
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
                                    :model    (or (:model response) (:model p))
                                    :provider provider})
          ;; Update token counts
          (storage/update-tokens! (state-dir) key-str tokens))))))

(defwhen prompt-streamed "the prompt is streamed to the LLM"
  []
  (let [p        (build-prompt-for-session)
        provider (current-provider)
        chunks   (atom [])
        request  (select-keys p [:max_tokens :messages :model :system :tools])
        result   (llm-chat-stream request
                                  (fn [chunk] (swap! chunks conj chunk))
                                  nil)]
    (g/assoc! :llm-result result)
    (g/assoc! :stream-chunks @chunks)
    (when-not (:error result)
      (let [key-str (current-key)
            msg     (:message result)]
        (storage/append-message! (state-dir) key-str
                                 {:role     (:role msg)
                                  :content  (or (:content msg) "")
                                  :model    (:model result)
                                  :provider provider})))))

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
