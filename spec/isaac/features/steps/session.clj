(ns isaac.features.steps.session
  (:require
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.features.matchers :as match]
    [isaac.cli.chat.single-turn :as single-turn]
    [isaac.llm.grover :as grover]
    [isaac.prompt.anthropic :as anthropic-prompt]
    [isaac.prompt.builder :as prompt]
    [isaac.session.key :as key]
    [isaac.session.bridge :as bridge]
    [isaac.session.context :as session-ctx]
    [isaac.logger :as log]
    [isaac.session.storage :as storage]
    [isaac.tool.registry :as tool-registry]))

;; region ----- Helpers -----

(defn- clean-dir! [path]
  (let [dir (io/file path)]
    (when (.exists dir)
      (doseq [f (reverse (file-seq dir))]
        (.delete f)))))

(defn- state-dir [] (g/get :state-dir))

(defn- current-session []
  (or (when-let [id (g/get :current-key)]
        (storage/get-session (state-dir) id))
      (first (storage/list-sessions (state-dir)))))

(defn- current-key []
  (or (g/get :current-key)
      (:id (current-session))))

(defn- current-provider []
  (or (:provider (current-session))
      (let [agents   (or (g/get :crew) (g/get :agents))
            models   (g/get :models)
            agent-id (or (:crew (current-session)) (:agent (current-session)))
            agent    (get agents agent-id)
            model    (get models (:model agent))]
        (:provider model))))

(defn- provider-config []
  (let [provider-name (current-provider)]
    (get (g/get :provider-configs) provider-name)))

(defn- current-agent-config []
  (let [agents   (or (g/get :crew) (g/get :agents))
        agent-id (or (:crew (current-session)) (:agent (current-session)) "main")]
    (get agents agent-id)))

(defn- current-model-config []
  (let [models   (g/get :models)
        session  (current-session)
        agent    (current-agent-config)
        model-id (or (:model session) (:model agent))]
    (or (get models model-id)
        (some (fn [[_ cfg]] (when (= model-id (:model cfg)) cfg)) models)
        (first (filter #(= model-id (:model %)) (vals models))))))

(defn- parse-model-content [content]
  (if (and (string? content)
           (str/starts-with? content "[")
           (str/ends-with? content "]"))
    (try
      (let [parsed (edn/read-string content)]
        (if (vector? parsed) parsed content))
      (catch Exception _
        content))
    content))

(defn- unquote-string [s]
  (if (and (string? s) (<= 2 (count s)) (str/starts-with? s "\"") (str/ends-with? s "\""))
    (subs s 1 (dec (count s)))
    s))

(defn- complete-turn! [{:keys [output request result]}]
  (g/dissoc! :turn-future)
  (g/assoc! :llm-result result)
  (g/assoc! :llm-request request)
  (g/assoc! :output output)
  result)

(defn await-turn! []
  (when-let [turn-future (g/get :turn-future)]
    (let [result (deref turn-future 30000 ::timeout)]
      (when (= ::timeout result)
        (throw (ex-info "turn did not complete within 30 seconds" {})))
      (complete-turn! result))))

(defn- await-acp-turn! []
  (when-let [turn-future (g/get :acp-turn-future)]
    (let [result (deref turn-future 2000 ::timeout)]
      (when (= ::timeout result)
        (throw (ex-info "ACP turn did not complete within 2 seconds" {})))
      (g/dissoc! :acp-turn-future)
      result)))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Given: Infrastructure -----

(defgiven empty-state "an empty Isaac state directory {string}"
  [path]
  (let [dir (if (and (str/starts-with? path "\"") (str/ends-with? path "\""))
              (subs path 1 (dec (count path)))
              path)]
    (grover/reset-queue!)
    (tool-registry/clear!)
    (log/set-output! :memory)
    (log/clear-entries!)
    (clean-dir! dir)
    (g/assoc! :state-dir dir)))

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
    (let [by-name (into {} (map (fn [a] [(:name a) a]) agents))]
      (g/assoc! :agents by-name)
      (g/assoc! :crew by-name))))

(defgiven crew-exist "the following crew exist:"
  [table]
  (agents-exist table))

(defgiven agent-has-tools "the agent has tools:"
  [table]
  (let [tools (mapv (fn [row]
                      (let [t (zipmap (:headers table) row)]
                        {:name        (get t "name")
                         :description (get t "description")
                         :parameters  (json/parse-string (get t "parameters") true)}))
                    (:rows table))]
    (g/assoc! :tools tools)
    (doseq [tool tools]
      (when-not (tool-registry/lookup (:name tool))
        (tool-registry/register! (assoc tool :handler (fn [_] {:result "ok"})))))))

(defgiven crew-has-tools "the crew member has tools:"
  [table]
  (agent-has-tools table))

(defgiven ollama-server-running "the Ollama server is running"
  []
  (g/update! :provider-configs
             (fn [m] (assoc (or m {}) "ollama" {:name "ollama" :baseUrl "http://localhost:11434"}))))

(defgiven ollama-model-available "model {model:string} is available in Ollama"
  [_model]
  nil)

(defgiven ollama-server-not-running "the Ollama server is not running"
  []
  (g/update! :provider-configs
             (fn [m] (assoc (or m {}) "ollama" {:name "ollama" :baseUrl "http://localhost:99999"}))))

(defgiven responses-queued "the following model responses are queued:"
  [table]
  (grover/reset-queue!)
  (let [responses (mapv (fn [row]
                          (let [m (zipmap (:headers table) row)]
                            (cond-> {}
                              (some? (get m "type"))
                              (assoc :type (get m "type"))

                              (some? (get m "content"))
                              (assoc :content (parse-model-content (get m "content")))

                              (get m "model")
                              (assoc :model (let [v (get m "model")] (when-not (str/blank? v) v)))

                              (let [v (get m "tool_call")]
                                (when-not (str/blank? v) v))
                              (assoc :tool_call (get m "tool_call"))

                              (let [v (get m "arguments")]
                                (when-not (str/blank? v) v))
                              (assoc :arguments (json/parse-string (get m "arguments") true)))))
                        (:rows table))]
    (grover/enqueue! responses)))

(defgiven llm-server-down "the LLM server is not running"
  []
  (g/assoc! :llm-error {:error :connection-refused :message "Could not connect to Ollama server"}))

(defgiven llm-response-delayed "the LLM response is delayed by {int} seconds"
  [_seconds]
  (grover/enable-delay!))

;; endregion ^^^^^ Given: Infrastructure ^^^^^

;; region ----- Given: Sessions & Transcripts -----

(defn- create-session-from-row! [row-map]
  (let [name   (get row-map "name")
        agent  (or (get row-map "crew")
                   (get row-map "agent")
                   (let [prefix (first (str/split name #"-" 2))]
                     (when (contains? (or (g/get :crew) (g/get :agents)) prefix) prefix)))
        entry  (or (storage/open-session (state-dir) name)
                   (storage/create-session! (state-dir) name {:crew agent :agent agent}))
        updates (cond-> {}
                  (get row-map "updatedAt")    (assoc :updatedAt (get row-map "updatedAt"))
                  (get row-map "cwd")          (assoc :cwd (get row-map "cwd"))
                  (get row-map "totalTokens")  (assoc :totalTokens (parse-long (get row-map "totalTokens")))
                  (get row-map "inputTokens")  (assoc :inputTokens (parse-long (get row-map "inputTokens")))
                  (get row-map "outputTokens") (assoc :outputTokens (parse-long (get row-map "outputTokens")))
                  (get row-map "compactionCount") (assoc :compactionCount (parse-long (get row-map "compactionCount"))))]
    (when (seq updates)
      (storage/update-session! (state-dir) (:id entry) updates))
    (g/assoc! :current-key (:id entry))
    entry))

(defgiven sessions-exist "the following sessions exist:"
  [table]
  (doseq [row (:rows table)]
    (create-session-from-row! (zipmap (:headers table) row))))

(defthen session-exists-quoted #"the session \"([^\"]+)\" exists"
  [session-name]
  (g/should-not-be-nil (storage/get-session (state-dir) session-name)))

(defgiven agent-has-sessions "agent {agent:string} has sessions:"
  [agent-id table]
  (doseq [row (:rows table)]
    (let [row-map  (zipmap (:headers table) row)
          key-str  (get row-map "key")]
      (storage/create-session! (state-dir) key-str)
      (let [updates (cond-> {}
                       (get row-map "inputTokens")  (assoc :inputTokens (parse-long (get row-map "inputTokens")))
                       (get row-map "outputTokens") (assoc :outputTokens (parse-long (get row-map "outputTokens")))
                       (get row-map "totalTokens")  (assoc :totalTokens (parse-long (get row-map "totalTokens")))
                       (get row-map "updatedAt")    (assoc :updatedAt (get row-map "updatedAt"))) ]
        (when (seq updates)
          (storage/update-session! (state-dir) key-str updates)))
      (g/assoc! :current-key key-str))))

(defgiven crew-has-sessions "crew {crew:string} has sessions:"
  [crew-id table]
  (agent-has-sessions crew-id table))

(defn- append-transcript-entry! [key-str row-map]
  (let [entry-type (get row-map "type" "message")]
    (case entry-type
      "compaction"
      (storage/append-compaction! (state-dir) key-str
                                  {:summary          (get row-map "summary")
                                   :firstKeptEntryId (get row-map "firstKeptEntryId")
                                   :tokensBefore     (some-> (get row-map "tokensBefore") parse-long)})
      "toolCall"
      (storage/append-message! (state-dir) key-str
                               {:role    "assistant"
                                :content [{:type      "toolCall"
                                           :id        (or (get row-map "id") (str (java.util.UUID/randomUUID)))
                                           :name      (get row-map "name")
                                           :arguments (or (some-> (get row-map "arguments") (json/parse-string true)) {})}]})
      "toolResult"
      (storage/append-message! (state-dir) key-str
                               {:role       "toolResult"
                                :toolCallId (get row-map "id")
                                :content    (get row-map "message.content")
                                :isError    (= "true" (get row-map "isError"))})
      ;; default: message
      (storage/append-message! (state-dir) key-str
                                (cond-> {:role    (get row-map "message.role")
                                         :content (get row-map "message.content")}
                                   (get row-map "message.model")    (assoc :model (get row-map "message.model"))
                                   (get row-map "message.provider") (assoc :provider (get row-map "message.provider"))
                                   (get row-map "message.crew")     (assoc :crew (get row-map "message.crew"))
                                   (get row-map "message.api")      (assoc :api (get row-map "message.api"))
                                  (get row-map "message.stopReason") (assoc :stopReason (get row-map "message.stopReason"))
                                  (or (get row-map "message.usage.input")
                                      (get row-map "message.usage.output"))
                                  (assoc :usage (cond-> {}
                                                  (get row-map "message.usage.input")
                                                  (assoc :input (parse-long (get row-map "message.usage.input")))
                                                  (get row-map "message.usage.output")
                                                  (assoc :output (parse-long (get row-map "message.usage.output")))) )
                                  (get row-map "message.channel")  (assoc :channel (get row-map "message.channel"))
                                  (get row-map "message.to")       (assoc :to (get row-map "message.to")))))))

(defgiven session-has-transcript "session {key:string} has transcript:"
  [key-str table]
  (g/assoc! :current-key key-str)
  (doseq [row (:rows table)]
    (let [row-map (zipmap (:headers table) row)]
      (append-transcript-entry! key-str row-map))))

(defgiven session-has-error-entry #"session \"([^\"]+)\" has an error entry \"([^\"]+)\""
  [key-str content]
  (storage/append-error! (state-dir) key-str {:content (unquote-string content)
                                              :error   ":llm-error"}))

;; endregion ^^^^^ Given: Sessions & Transcripts ^^^^^

;; region ----- When -----

(defwhen session-created-randomly "a session is created with a random name"
  []
  (let [entry (storage/create-session! (state-dir) nil)]
    (g/assoc! :current-key (:id entry))))

(defwhen session-created-with-name-quoted #"a session is created with name \"([^\"]+)\""
  [session-name]
  (try
    (let [entry (storage/create-session! (state-dir) session-name)]
      (g/assoc! :current-key (:id entry))
      (g/dissoc! :error))
    (catch clojure.lang.ExceptionInfo e
      (g/assoc! :error (.getMessage e)))))

(defwhen session-opened "session {string} is opened"
  [session-name]
  (let [name  (unquote-string session-name)
        entry (storage/open-session (state-dir) name)]
    (g/assoc! :current-key (:id entry))))

(defwhen sessions-created-for-agent "sessions are created for agent {agent:string}:"
  [agent-id table]
  (doseq [row (:rows table)]
    (let [row-map (zipmap (:headers table) row)]
      (if (get row-map "key")
        (do (storage/create-session! (state-dir) (get row-map "key"))
            (g/assoc! :current-key (get row-map "key")))
        (let [parent-key (get row-map "parentKey")
              thread     (get row-map "thread")]
          (if parent-key
            (let [key-str (key/build-thread-key parent-key thread)]
              (storage/create-session! (state-dir) key-str)
              (g/assoc! :current-key key-str))
            (let [kw-map  (into {} (map (fn [[k v]] [(keyword k) v]) row-map))
                  key-str (key/build-key kw-map)]
              (storage/create-session! (state-dir) key-str)
              (g/assoc! :current-key key-str))))))))

(defwhen sessions-created-for-crew "sessions are created for crew {crew:string}:"
  [crew-id table]
  (sessions-created-for-agent crew-id table))

(defwhen entries-appended "entries are appended to session {key:string}:"
  [key-str table]
  (g/assoc! :current-key key-str)
  (doseq [row (:rows table)]
    (let [row-map (zipmap (:headers table) row)]
      (append-transcript-entry! key-str row-map))))

(defwhen user-sends-on-session #"the user sends \"(.+)\" on session \"([^\"]+)\"$"
  [content key-str]
  (g/assoc! :current-key key-str)
  (let [agent-cfg  (current-agent-config)
        model-cfg  (current-model-config)
        provider   (:provider model-cfg)
        send-opts  {:model          (:model model-cfg)
                    :crew-members   (or (g/get :crew) (g/get :agents))
                    :models         (g/get :models)
                    :soul           (:soul agent-cfg)
                    :provider       provider
                    :provider-config (provider-config)
                    :context-window (:contextWindow model-cfg)}]
    (let [turn-future (future
                        (let [result (atom nil)
                              output (with-out-str
                                       (try
                                         (reset! result (single-turn/process-user-input! (state-dir) key-str content send-opts))
                                         (catch Exception e
                                           (reset! result {:error :exception :message (.getMessage e)}))))]
                          {:output  output
                           :request (grover/last-request)
                           :result  @result}))]
      (g/assoc! :turn-future turn-future)
      (let [result (deref turn-future 50 ::pending)]
        (when-not (= ::pending result)
          (complete-turn! result))))))

(defwhen turn-cancelled "the turn is cancelled on session {key:string}"
  [key-str]
  (bridge/cancel! key-str)
  (grover/release-delay!)
  (await-turn!))

(defwhen prompt-built-for-provider #"the prompt for session \"([^\"]+)\" is built for provider \"([^\"]+)\""
  [key-str provider]
  (g/assoc! :current-key key-str)
  (let [session    (storage/get-session (state-dir) key-str)
        agent-id   (or (:crew session) (:agent session) "main")
        agents     (or (g/get :crew) (g/get :agents))
        models     (g/get :models)
        agent-cfg  (get agents agent-id)
        model-cfg  (current-model-config)
        ctx        (session-ctx/resolve-turn-context {:agents agents
                                                      :models models
                                                      :cwd    (:cwd session)
                                                      :home   (state-dir)}
                                                     agent-id)
        soul       (if-let [boot-files (:boot-files ctx)]
                     (str (:soul agent-cfg) "\n\n" boot-files)
                     (:soul agent-cfg))
        provider'  (unquote-string provider)
        builder    (if (str/starts-with? provider' "anthropic")
                     anthropic-prompt/build
                     prompt/build)
        prompt-msg (builder {:model      (:model model-cfg)
                             :soul       soul
                             :provider   provider'
                             :transcript (storage/get-transcript (state-dir) key-str)})]
    (g/assoc! :built-prompt prompt-msg)))

(defgiven file-exists-with #"the file \"([^\"]+)\" exists with:$"
  [path content]
  (io/make-parents path)
  (spit path content))

;; endregion ^^^^^ When ^^^^^

;; region ----- Then -----

(defthen error-contains-quoted #"the error contains \"([^\"]+)\""
  [expected]
  (g/should (str/includes? (or (g/get :error) "") expected)))

(defthen session-count-is "the session count is {int}"
  [n]
  (let [n (if (string? n) (parse-long n) n)]
    (g/should= n (count (storage/list-sessions (state-dir))))))

(defn- session-match-entry [entry]
  (assoc entry
         :crew (or (:crew entry) (:agent entry))
         :file (str (state-dir) "/sessions/" (:sessionFile entry))))

(defthen sessions-match "the following sessions match:"
  [table]
  (let [listing (mapv session-match-entry (storage/list-sessions (state-dir)))
        result  (match/match-entries table listing)]
    (g/should= [] (:failures result))))

(defthen session-file-is-quoted #"the session file is \"([^\"]+)\""
  [expected-path]
  (let [entry (current-session)]
    (g/should= expected-path (str (state-dir) "/sessions/" (:sessionFile entry)))))

(defthen most-recent-session-is "the most recent session is {string}"
  [session-name]
  (let [expected (unquote-string session-name)
        entry     (storage/most-recent-session (state-dir))]
    (g/should= expected (:id entry))))

(defthen agent-session-count #"agent \"([^\"]+)\" has (\d+) sessions?"
  [agent-id n]
  (let [listing (storage/list-sessions (state-dir) agent-id)]
    (g/should= (parse-long n) (count listing))))

(defthen crew-session-count #"crew \"([^\"]+)\" has (\d+) sessions?"
  [crew-id n]
  (let [listing (storage/list-sessions (state-dir) crew-id)]
    (g/should= (parse-long n) (count listing))))

(defthen agent-sessions-matching "agent {agent:string} has sessions matching:"
  [agent-id table]
  (let [listing (storage/list-sessions (state-dir) agent-id)
        result  (match/match-entries table listing)]
    (g/should= [] (:failures result))))

(defthen crew-sessions-matching "crew {crew:string} has sessions matching:"
  [crew-id table]
  (let [listing (map session-match-entry (storage/list-sessions (state-dir) crew-id))
        result  (match/match-entries table listing)]
    (g/should= [] (:failures result))))

(defthen session-transcript-count #"session \"([^\"]+)\" has (\d+) transcript entr(?:y|ies)"
  [key-str n]
  (let [transcript (storage/get-transcript (state-dir) key-str)]
    (g/should= (parse-long n) (count transcript))))

(defthen session-transcript-matching "session {key:string} has transcript matching:"
  [key-str table]
  (await-turn!)
  (await-acp-turn!)
  (let [transcript (storage/get-transcript (state-dir) key-str)
        explicit-idx? (some #(contains? % "#index") (map #(zipmap (:headers table) %) (:rows table)))
        wants-session? (some #(= "session" (get % "type")) (map #(zipmap (:headers table) %) (:rows table)))
        transcript (if (or explicit-idx? wants-session?)
                     transcript
                     (vec (remove #(= "session" (:type %)) transcript)))
        result     (match/match-entries table transcript)]
    (g/should= [] (:failures result))))

(defthen prompt-on-session-matches "the prompt \"{content:string}\" on session {key:string} matches:"
  [content key-str table]
  (g/assoc! :current-key key-str)
  (storage/append-message! (state-dir) key-str {:role "user" :content content})
  (let [transcript (storage/get-transcript (state-dir) key-str)
        agent-id   (or (:crew (storage/get-session (state-dir) key-str))
                       (:agent (storage/get-session (state-dir) key-str))
                       "main")
        agents     (or (g/get :crew) (g/get :agents))
        models     (g/get :models)
        agent-cfg  (get agents agent-id)
        model-cfg  (get models (:model agent-cfg))
        tools      (g/get :tools)
        builder    (if (= "anthropic" (:provider model-cfg))
                     anthropic-prompt/build
                     prompt/build)
        ctx        (session-ctx/resolve-turn-context {:agents agents
                                                      :models models
                                                      :cwd    (:cwd (storage/get-session (state-dir) key-str))
                                                      :home   (state-dir)}
                                                     agent-id)
        soul       (if-let [boot-files (:boot-files ctx)]
                     (str (:soul ctx) "\n\n" boot-files)
                     (:soul ctx))
        p          (builder {:model          (:model model-cfg)
                             :soul           soul
                             :transcript     transcript
                             :tools          tools
                             :context-window (:contextWindow model-cfg)})
        result     (match/match-object table p)]
    (g/should= [] (:failures result))))

(defthen session-index-has-keys "the session index has keys:"
  [table]
  (let [index-path (str (state-dir) "/sessions/index.edn")
        index-map  (edn/read-string (slurp index-path))
        actual     (set (keys index-map))
        expected   (set (map first (:rows table)))]
    (g/should= expected actual)))

(defthen system-prompt-contains #"the system prompt contains \"([^\"]+)\""
  [text]
  (let [prompt (get-in (g/get :llm-request) [:messages 0 :content])]
    (g/should (str/includes? (or prompt "") text))))

(defthen system-prompt-not-contains #"the system prompt does not contain \"([^\"]+)\""
  [text]
  (let [prompt (get-in (g/get :llm-request) [:messages 0 :content])]
    (g/should-not (str/includes? (or prompt "") text))))

(defthen turn-result-is "the turn result is {string}"
  [expected]
  (await-turn!)
  (g/should= (unquote-string expected)
             (or (:stopReason (g/get :llm-result))
                 (some-> (g/get :llm-result) :error name))))

(defthen session-has-no-role #"session \"([^\"]+)\" has no transcript entries with role \"([^\"]+)\""
  [key-str role]
  (let [entries (storage/get-transcript (state-dir) key-str)
        role    (unquote-string role)]
    (g/should-not (some #(= role (get-in % [:message :role])) entries))))

(defthen prompt-messages-contain-tool-call "the prompt messages contain a tool call with:"
  [table]
  (let [messages (:messages (g/get :built-prompt))
        tc-msg   (first (filter #(contains? % :tool_calls) messages))
        result   (match/match-object table tc-msg)]
    (g/should= [] (:failures result))))

(defthen prompt-messages-contain-tool-result "the prompt messages contain a tool result with:"
  [table]
  (let [messages (:messages (g/get :built-prompt))
        tr-msg   (first (filter #(= "tool" (:role %)) messages))
        result   (match/match-object table tr-msg)]
    (g/should= [] (:failures result))))

(defthen prompt-messages-do-not-contain-key #"the prompt messages do not contain key \"([^\"]+)\""
  [key-name]
  (let [messages (:messages (g/get :built-prompt))
        kw       (keyword (unquote-string key-name))]
    (g/should-not (some #(contains? % kw) messages))))

(defthen prompt-messages-do-not-contain-role #"the prompt messages do not contain role \"([^\"]+)\""
  [role]
  (let [role     (unquote-string role)
        messages (:messages (g/get :built-prompt))]
    (g/should-not (some #(= role (:role %)) messages))))

(defthen tool-loop-request-contains "the tool loop request contains messages with:"
  [table]
  (let [key-str       (current-key)
        session       (storage/get-session (state-dir) key-str)
        transcript    (storage/get-transcript (state-dir) key-str)
        agent-id      (or (:crew session) (:agent session) "main")
        agents        (or (g/get :crew) (g/get :agents))
        models        (g/get :models)
        model-cfg     (current-model-config)
        ctx           (session-ctx/resolve-turn-context {:agents agents
                                                         :models models
                                                         :cwd    (:cwd session)
                                                         :home   (state-dir)}
                                                        agent-id)
        provider-name (or (some (fn [[name cfg]]
                                  (when (= "openai-compatible" (:api cfg))
                                    name))
                                (g/get :provider-configs))
                          (current-provider))
        built-request (single-turn/build-chat-request provider-name
                                                     (get (g/get :provider-configs) provider-name)
                                                     {:boot-files (:boot-files ctx)
                                                      :model      (:model model-cfg)
                                                      :soul       (:soul ctx)
                                                      :transcript transcript})
        result        (match/match-entries table (:messages built-request))]
    (g/should= [] (:failures result))))

;; endregion ^^^^^ Then ^^^^^
