(ns isaac.features.steps.session
  (:require
    [c3kit.apron.env :as c3env]
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.set :as set]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.config.change-source :as change-source]
    [isaac.config.loader :as config]
    [isaac.features.matchers :as match]
    [isaac.fs :as fs]
    [isaac.drive.turn :as single-turn]
    [isaac.config.loader :as config-loader]
    [isaac.llm.grover :as grover]
    [isaac.prompt.anthropic :as anthropic-prompt]
    [isaac.prompt.builder :as prompt]
    [isaac.session.compaction :as session-compaction]
    [isaac.session.key :as key]
    [isaac.session.bridge :as bridge]
    [isaac.session.context :as session-ctx]
    [isaac.logger :as log]
    [isaac.session.storage :as storage]
    [isaac.tool.memory :as memory]
    [isaac.tool.registry :as tool-registry]))

(g/before-scenario g/reset!)

;; region ----- Helpers -----

(defn- clean-dir! [path]
  (let [dir (io/file path)]
    (when (.exists dir)
      (doseq [f (-> dir file-seq reverse butlast)]
        (.delete f)))))

(defn- state-dir [] (g/get :state-dir))

(defn- mem-fs []
  (or (g/get :mem-fs) fs/*fs*))

(defn- with-feature-fs [f]
  (binding [fs/*fs* (mem-fs)]
    (f)))

(defn- notify-config-change! [path]
  (when-let [source (g/get :config-change-source)]
    (change-source/notify-path! source path)))

(defn- with-current-time [f]
  (if-let [current-time (g/get :current-time)]
    (binding [memory/*now* current-time]
      (f))
    (f)))

(defn- current-session []
  (with-feature-fs
    #(or (when-let [id (g/get :current-key)]
           (storage/get-session (state-dir) id))
         (first (storage/list-sessions (state-dir))))))

(defn- current-key []
  (or (g/get :current-key)
      (:id (current-session))))

(declare current-model-config)

(defn- current-provider []
  (or (:provider (current-session))
      (:provider (current-model-config))))

(defn- loaded-config []
  (with-feature-fs #(config/load-config {:home (state-dir)})))

(defn- merged-agents []
  (or (:crew (loaded-config)) {}))

(defn- loaded-models []
  (or (:models (loaded-config)) {}))

(defn- provider-config []
  (let [provider-name (current-provider)]
    (or (get (g/get :provider-configs) provider-name)
        (get-in (loaded-config) [:providers provider-name]))))

(defn- current-agent-config []
  (let [agent-id (or (:crew (current-session)) (:agent (current-session)) "main")]
    (get (merged-agents) agent-id)))

(defn- crew-config-path [crew-id]
  (str (state-dir) "/.isaac/config/crew/" crew-id ".edn"))

(defn- configured-crew-ids []
  (with-feature-fs
    (fn []
      (let [dir (str (state-dir) "/.isaac/config/crew")]
        (->> (or (fs/children dir) [])
             (filter #(str/ends-with? % ".edn"))
             (map #(subs % 0 (- (count %) 4)))
             sort
             vec)))))

(defn- active-crew-id []
  (or (:crew (current-session))
      (:agent (current-session))
      (when (= 1 (count (configured-crew-ids)))
        (first (configured-crew-ids)))
      (get-in (loaded-config) [:defaults :crew])
      "main"))

(defn- update-crew-config! [crew-id f]
  (with-feature-fs
    (fn []
      (let [path    (crew-config-path crew-id)
            current (if (fs/exists? path) (edn/read-string (fs/slurp path)) {})
            updated (f current)]
        (fs/mkdirs (fs/parent path))
        (fs/spit path (pr-str updated))))))

(defn- current-model-config []
  (let [models    (loaded-models)
        session   (current-session)
        agent     (current-agent-config)
        defaults  (:defaults (loaded-config))
        model-id  (or (:model session) (:model agent) (:model defaults))]
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
    (some-> content (str/replace "\\n" "\n"))))

(defn- prompt-tools []
  (vec (or (:tools (g/get :llm-request)) [])))

(defn- prompt-tool-name [tool]
  (or (:name tool)
      (get-in tool [:function :name])))

(defn- header-row? [row]
  (and (= "model" (first row))
       (every? #{"model" "type" "content" "tool_call" "arguments"} row)))

(defn- queued-response-row->map [headers row]
  (let [m         (zipmap headers row)
        tool-name (or (get m "tool_call") (get m "tool"))
        arguments (get m "arguments")]
    (cond-> {}
      (some? (get m "type"))
      (assoc :type (get m "type"))

      (or (some? (get m "content"))
          (and (not (str/blank? arguments)) (str/blank? tool-name)))
      (assoc :content (parse-model-content (or (get m "content") arguments)))

      (get m "model")
      (assoc :model (let [v (get m "model")] (when-not (str/blank? v) v)))

      (let [v tool-name]
        (when-not (str/blank? v) v))
      (assoc :tool_call tool-name)

      (and (not (str/blank? tool-name))
           (not (str/blank? arguments)))
      (assoc :arguments (json/parse-string arguments true)))))

(defn- queued-responses [table]
  (loop [headers   (:headers table)
         rows      (:rows table)
         responses []]
    (if-let [row (first rows)]
      (if (header-row? row)
        (recur row (rest rows) responses)
        (recur headers (rest rows) (conj responses (queued-response-row->map headers row))))
      responses)))

(defn- unquote-string [s]
  (if (and (string? s) (<= 2 (count s)) (str/starts-with? s "\"") (str/ends-with? s "\""))
    (subs s 1 (dec (count s)))
    s))

(defn- complete-turn! [{:keys [output request result]}]
  (g/dissoc! :turn-future)
  (g/assoc! :llm-result result)
  (g/assoc! :llm-request request)
  (g/assoc! :provider-request (grover/last-provider-request))
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

(defn- seed-cwd-files! [mem]
  (let [cwd (System/getProperty "user.dir")
        agents-md (str cwd "/AGENTS.md")]
    (when (.exists (io/file agents-md))
      (binding [fs/*fs* mem]
        (fs/spit agents-md (slurp agents-md))))))

(defn- ->state-dir [dir virtual?]
  (if (str/starts-with? dir "/")
    dir
    (if (or virtual? (not (str/includes? dir "/")))
      (str "/" dir)
      (str (System/getProperty "user.dir") "/" dir))))

(def ^:private minimal-config
  {:defaults  {:crew "main"
               :model "llama"}
   :crew      {"main" {}}
   :models    {"llama" {:model          "llama3.3:1b"
                         :provider       "ollama"
                         :context-window 32768}}
   :providers {"ollama" {:api      "ollama"
                          :base-url "http://localhost:11434"}}})

(defn- seed-minimal-config! [path]
  (let [config-path (str path "/.isaac/config/isaac.edn")]
    (fs/mkdirs (fs/parent config-path))
    (fs/spit config-path (pr-str minimal-config))))

(defn- initialize-state-dir! [path virtual?]
  (let [dir (if (and (str/starts-with? path "\"") (str/ends-with? path "\""))
              (subs path 1 (dec (count path)))
              path)
        abs-dir  (->state-dir dir virtual?)
        mem      (when virtual? (fs/mem-fs))]
    (g/reset!)
    (grover/reset-queue!)
    (reset! c3env/-overrides {})
    (config-loader/clear-env-overrides!)
    (tool-registry/clear!)
    (single-turn/clear-async-compactions!)
    (log/set-output! :memory)
    (log/clear-entries!)
    (if virtual?
      (do
        (seed-cwd-files! mem)
        (binding [fs/*fs* mem]
          (fs/mkdirs abs-dir))
        (g/assoc! :mem-fs mem))
      (do
        (clean-dir! abs-dir)
        (g/dissoc! :mem-fs)))
    (g/assoc! :state-dir abs-dir)))

(defgiven empty-state "an empty Isaac state directory {string}"
  "Real-fs state dir when path is absolute or contains '/'; in-memory
   otherwise. Clean slate — deletes any existing content first. No
   config files are seeded. Use 'in-memory Isaac state directory' if
   the scenario needs a seeded minimal config."
  [path]
  (let [dir (if (and (str/starts-with? path "\"") (str/ends-with? path "\""))
              (subs path 1 (dec (count path)))
              path)]
    (initialize-state-dir! path (not (or (str/starts-with? dir "/")
                                         (str/includes? dir "/"))))))

(defgiven in-memory-state "an in-memory Isaac state directory {string}"
  "Virtual fs (mem-fs) rooted at the given path. Seeds a minimal
   isaac.edn at <path>/.isaac/config/isaac.edn so config loaders have
   something to parse. For a bare state dir without the seed, use
   'an empty Isaac state directory'."
  [path]
  (initialize-state-dir! path true)
  (with-feature-fs #(seed-minimal-config! (state-dir))))

(defn- write-grover-defaults! []
  (let [root (str (state-dir) "/.isaac/config")]
    (fs/mkdirs root)
    (fs/spit (str root "/isaac.edn")
             (pr-str {:defaults {:crew "main" :model "grover"}}))
    (fs/mkdirs (str root "/models"))
    (fs/mkdirs (str root "/crew"))
    (fs/spit (str root "/models/grover.edn")
             (pr-str {:model "echo" :provider :grover :context-window 32768}))
    (fs/spit (str root "/crew/main.edn")
             (pr-str {:model :grover :soul "You are Isaac."}))))

(defgiven default-grover-setup "default Grover setup"
  "One-line Background: in-memory state dir at target/test-state plus
   grover provider, echo model, main crew with soul 'You are Isaac.'
   on disk. Use as the baseline for any feature that just needs a
   working crew/model combo; override pieces afterward as needed."
  []
  (initialize-state-dir! "target/test-state" true)
  (with-feature-fs write-grover-defaults!))

(defgiven default-grover-setup-in "default Grover setup in {dir:string}"
  "Same as 'default Grover setup' but at a custom state-dir path."
  [dir]
  (initialize-state-dir! dir true)
  (with-feature-fs write-grover-defaults!))

(defgiven agent-has-tools "the agent has tools:"
  [table]
  (let [tools (mapv (fn [row]
                      (let [t (zipmap (:headers table) row)]
                        {:name        (get t "name")
                         :description (get t "description")
                         :parameters  (json/parse-string (get t "parameters") true)}))
                    (:rows table))
        allow   (mapv (comp keyword :name) tools)
        crew-id (active-crew-id)]
    (g/assoc! :tools tools)
    (doseq [tool tools]
      (when-not (tool-registry/lookup (:name tool))
        (tool-registry/register! (assoc tool :handler (fn [_] {:result "ok"})))))
    (update-crew-config! crew-id #(assoc % :tools {:allow allow}))))

(defgiven crew-has-tools "the crew member has tools:"
  "Registers the listed tools with the tool-registry and sets each
   crew member's :tools.allow to the names. Tools not already registered
   get a no-op handler. Table columns: name, description, parameters
   (JSON). Applies to ALL crew in the :crew atom, not just one."
  [table]
  (agent-has-tools table))

(defgiven ollama-server-running "the Ollama server is running"
  "Sets the test 'ollama' provider-config to localhost:11434. Does not
   actually start ollama — assumes a real server is reachable for
   integration tests (or grover is acting as one in the test double)."
  []
  (g/update! :provider-configs
             (fn [m] (assoc (or m {}) "ollama" {:name "ollama" :base-url "http://localhost:11434"}))))

(defgiven ollama-model-available "model {model:string} is available in Ollama"
  [_model]
  nil)

(defgiven ollama-server-not-running "the Ollama server is not running"
  "Sets the 'ollama' provider-config to an unreachable port (99999) so
   provider calls fail with connection-refused. Used to test
   connection-failure handling."
  []
  (g/update! :provider-configs
             (fn [m] (assoc (or m {}) "ollama" {:name "ollama" :base-url "http://localhost:99999"}))))

(defgiven responses-queued "the following model responses are queued:"
  "Clears and re-populates the grover response queue. Each table row is
   one chunk/event the mock will emit in order. Columns: 'type' (text /
   tool_call / error), 'content' or 'tool_call' + 'arguments', 'model'.
   For streaming, enqueue multiple rows; they come out as distinct
   chunks."
  [table]
  (grover/reset-queue!)
  (let [responses (queued-responses table)]
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
  (with-feature-fs
    (fn []
      (let [name   (get row-map "name")
            agent  (or (get row-map "crew")
                       (get row-map "agent")
                        (let [prefix (first (str/split name #"-" 2))]
                          (when (contains? (merged-agents) prefix) prefix)))
            entry  (or (storage/open-session (state-dir) name)
                       (storage/create-session! (state-dir) name {:crew agent :agent agent :cwd (state-dir)}))
            compaction (cond-> {}
                         (get row-map "compaction.strategy")  (assoc :strategy (keyword (get row-map "compaction.strategy")))
                         (get row-map "compaction.threshold") (assoc :threshold (parse-long (get row-map "compaction.threshold")))
                         (get row-map "compaction.tail")      (assoc :tail (parse-long (get row-map "compaction.tail")))
                         (or (get row-map "compaction.async?")
                             (get row-map "compaction.async"))
                         (assoc :async? (= "true" (or (get row-map "compaction.async?")
                                                       (get row-map "compaction.async")))))
            updates (cond-> {}
                      (get row-map "updatedAt")    (assoc :updatedAt (get row-map "updatedAt"))
                      (get row-map "cwd")          (assoc :cwd (let [cwd (get row-map "cwd")]
                                                                  (if (str/starts-with? cwd "/")
                                                                    cwd
                                                                    (str (System/getProperty "user.dir") "/" cwd))))
                      (get row-map "totalTokens")  (assoc :totalTokens (parse-long (get row-map "totalTokens")))
                      (get row-map "inputTokens")  (assoc :inputTokens (parse-long (get row-map "inputTokens")))
                      (get row-map "outputTokens") (assoc :outputTokens (parse-long (get row-map "outputTokens")))
                      (get row-map "compactionCount") (assoc :compactionCount (parse-long (get row-map "compactionCount")))
                      (seq compaction) (assoc :compaction compaction))]
        (when (seq updates)
          (storage/update-session! (state-dir) (:id entry) updates))
        (g/assoc! :current-key (:id entry))
        entry))))

(defgiven sessions-exist "the following sessions exist:"
  "Creates sessions on disk via storage/create-session! (NOT the :crew
   test atom). Columns: name (session key), optionally crew/agent,
   cwd, updatedAt, totalTokens, inputTokens, outputTokens,
   compactionCount, compaction.strategy/threshold/tail/async?. Writes
   the transcript directory and session index."
  [table]
  (doseq [row (:rows table)]
    (create-session-from-row! (zipmap (:headers table) row))))

(defthen session-exists-quoted #"the session \"([^\"]+)\" exists"
  [session-name]
  (g/should-not-be-nil (with-feature-fs #(storage/get-session (state-dir) session-name))))

(defthen session-exists #"session \"([^\"]+)\" exists"
  [session-name]
  (g/should-not-be-nil (with-feature-fs #(storage/get-session (state-dir) session-name))))

(defthen session-does-not-exist #"session \"([^\"]+)\" does not exist"
  [session-name]
  (g/should-be-nil (with-feature-fs #(storage/get-session (state-dir) session-name))))

(defgiven agent-has-sessions "agent {agent:string} has sessions:"
  [agent-id table]
  (doseq [row (:rows table)]
    (with-feature-fs
      (fn []
        (let [row-map  (zipmap (:headers table) row)
              key-str  (get row-map "key")]
          (storage/create-session! (state-dir) key-str {:cwd (state-dir)})
          (let [updates (cond-> {}
                           (get row-map "inputTokens")  (assoc :inputTokens (parse-long (get row-map "inputTokens")))
                           (get row-map "outputTokens") (assoc :outputTokens (parse-long (get row-map "outputTokens")))
                           (get row-map "totalTokens")  (assoc :totalTokens (parse-long (get row-map "totalTokens")))
                           (get row-map "updatedAt")    (assoc :updatedAt (get row-map "updatedAt")))]
            (when (seq updates)
              (storage/update-session! (state-dir) key-str updates)))
          (g/assoc! :current-key key-str))))))

(defgiven crew-has-sessions "crew {crew:string} has sessions:"
  [crew-id table]
  (agent-has-sessions crew-id table))

(defn- append-transcript-entry! [key-str row-map]
  (with-feature-fs
    (fn []
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
                                   (get row-map "tokens")          (assoc :tokens (parse-long (get row-map "tokens")))
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
                                 (get row-map "message.to")       (assoc :to (get row-map "message.to")))))))))

(defgiven session-has-transcript "session {key:string} has transcript:"
  "Appends transcript entries to an existing session. The 'type' column
   picks the entry kind: message (default, role+content), compaction
   (summary+firstKeptEntryId+tokensBefore), toolCall (name+arguments+id),
   toolResult (id+content+isError). Additional columns populate optional
   fields (message.model, message.usage.input, etc.)."
  [key-str table]
  (g/assoc! :current-key key-str)
  (doseq [row (:rows table)]
    (let [row-map (zipmap (:headers table) row)]
      (append-transcript-entry! key-str row-map))))

(defgiven session-has-error-entry #"session \"([^\"]+)\" has an error entry \"([^\"]+)\""
  [key-str content]
  (with-feature-fs
    #(storage/append-error! (state-dir) key-str {:content (unquote-string content)
                                                 :error   ":llm-error"})))

;; endregion ^^^^^ Given: Sessions & Transcripts ^^^^^

;; region ----- When -----

(defwhen session-created-randomly "a session is created with a random name"
  []
  (let [entry (with-feature-fs #(storage/create-session! (state-dir) nil {:cwd (state-dir)}))]
    (g/assoc! :current-key (:id entry))))

(defwhen session-created-without-name "a session is created without a name"
  []
  (let [entry (with-feature-fs #(storage/create-session! (state-dir) nil {:cwd (state-dir)}))]
    (g/assoc! :last-session entry)
    (g/assoc! :current-key (:id entry))))

(defwhen session-created-with-name-quoted #"a session is created with name \"([^\"]+)\""
  [session-name]
  (try
    (let [entry (with-feature-fs #(storage/create-session! (state-dir) session-name {:cwd (state-dir)}))]
      (g/assoc! :current-key (:id entry))
      (g/dissoc! :error))
    (catch clojure.lang.ExceptionInfo e
      (g/assoc! :error (.getMessage e)))))

(defwhen session-created-named #"a session is created named \"([^\"]+)\""
  [session-name]
  (let [entry (with-feature-fs #(storage/create-session! (state-dir) session-name {:cwd (state-dir)}))]
    (g/assoc! :last-session entry)
    (g/assoc! :current-key (:id entry))))

(defwhen session-opened "session {string} is opened"
  [session-name]
  (let [name  (unquote-string session-name)
        entry (with-feature-fs #(storage/open-session (state-dir) name))]
    (g/assoc! :current-key (:id entry))))

(defwhen sessions-created-for-agent "sessions are created for agent {agent:string}:"
  [agent-id table]
  (doseq [row (:rows table)]
    (with-feature-fs
      (fn []
        (let [row-map (zipmap (:headers table) row)]
          (if (get row-map "key")
            (do (storage/create-session! (state-dir) (get row-map "key") {:cwd (state-dir)})
                (g/assoc! :current-key (get row-map "key")))
            (let [parent-key (get row-map "parentKey")
                  thread     (get row-map "thread")]
              (if parent-key
                (let [key-str (key/build-thread-key parent-key thread)]
                  (storage/create-session! (state-dir) key-str {:cwd (state-dir)})
                  (g/assoc! :current-key key-str))
                (let [kw-map  (into {} (map (fn [[k v]] [(keyword k) v]) row-map))
                      key-str (key/build-key kw-map)]
                  (storage/create-session! (state-dir) key-str {:cwd (state-dir)})
                  (g/assoc! :current-key key-str))))))))))

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
  "Drives a full turn via single-turn/process-user-input! (in-memory,
   bypasses ACP/HTTP). Runs in a background future; waits 50ms and calls
   complete-turn! if done. Captures :llm-request (grover/last-request),
   :llm-result, :output. Use 'await-turn!' or a later step to force
   completion for async compaction scenarios."
  [content key-str]
  (g/assoc! :current-key key-str)
  (let [agent-cfg  (current-agent-config)
        model-cfg  (current-model-config)
        provider   (:provider model-cfg)
        send-opts  {:model          (:model model-cfg)
                    :crew-members   (merged-agents)
                    :models         (loaded-models)
                    :soul           (:soul agent-cfg)
                    :provider       provider
                    :provider-config (provider-config)
                    :context-window (:context-window model-cfg)}]
    (let [turn-future (future
                        (let [result (atom nil)
                              output (with-out-str
                                       (with-feature-fs
                                         (fn []
                                           (with-current-time
                                             (fn []
                                               (try
                                                 (reset! result (single-turn/process-user-input! (state-dir) key-str content send-opts))
                                                 (catch Exception e
                                                   (reset! result {:error :exception :message (.getMessage e)}))))))))]
                          {:output  output
                           :request (grover/last-request)
                           :result  @result}))]
      (g/assoc! :turn-future turn-future)
      (let [result (deref turn-future 50 ::pending)]
        (when-not (= ::pending result)
          (complete-turn! result))))))

(defwhen turn-cancelled "the turn is cancelled on session {key:string}"
  "Cancels the running turn via bridge/cancel!, releases any grover
   delay, and waits for the turn future. Pairs with 'the LLM response
   is delayed by N seconds' to test mid-turn cancellation."
  [key-str]
  (bridge/cancel! key-str)
  (grover/release-delay!)
  (await-turn!))

(defwhen async-compaction-completes #"the async compaction for session \"([^\"]+)\" completes"
  [key-str]
  (await-turn!)
  (single-turn/await-async-compaction! key-str))

(defwhen prompt-built-for-provider #"the prompt for session \"([^\"]+)\" is built for provider \"([^\"]+)\""
  "Synthetically builds a prompt for an existing session + provider
   (anthropic or prompt/build fallback) and stores it in :built-prompt.
   Does NOT actually run a turn — no LLM is called, no transcript is
   mutated. Use for asserting prompt shape on its own."
  [key-str provider]
  (g/assoc! :current-key key-str)
  (with-feature-fs
    (fn []
      (let [session    (storage/get-session (state-dir) key-str)
            agent-id   (or (:crew session) (:agent session) "main")
            cfg        (loaded-config)
            model-cfg  (current-model-config)
            ctx        (session-ctx/resolve-turn-context {:cfg    cfg
                                                          :cwd    (:cwd session)
                                                          :home   (state-dir)}
                                                         agent-id)
            soul       (if-let [boot-files (:boot-files ctx)]
                         (str (:soul ctx) "\n\n" boot-files)
                         (:soul ctx))
            provider'  (unquote-string provider)
            builder    (if (str/starts-with? provider' "anthropic")
                         anthropic-prompt/build
                         prompt/build)
            prompt-msg (builder {:model      (:model model-cfg)
                                 :soul       soul
                                 :provider   provider'
                                 :transcript (storage/get-transcript (state-dir) key-str)})]
        (g/assoc! :built-prompt prompt-msg)))))

(defgiven file-exists-with #"the file \"([^\"]+)\" exists with:$"
  [path content]
  (with-feature-fs
    (fn []
      (let [abs-path (if (str/starts-with? path "/")
                       path
                       (str (System/getProperty "user.dir") "/" path))]
        (fs/mkdirs (fs/parent abs-path))
        (fs/spit abs-path content)
        (notify-config-change! abs-path)))))

(defgiven given-file-contains #"file \"([^\"]+)\" contains \"([^\"]*)\""
  [path content]
  (with-feature-fs
    (fn []
      (let [abs-path (if (str/starts-with? path "/")
                       path
                       (str (System/getProperty "user.dir") "/" path))]
        (fs/mkdirs (fs/parent abs-path))
        (fs/spit abs-path content)
        (notify-config-change! abs-path)))))

(defthen then-file-contains #"the file \"([^\"]+)\" contains \"([^\"]*)\""
  [path content]
  (with-feature-fs
    (fn []
      (let [root-name (.getName (io/file (state-dir)))
            abs-path  (cond
                        (str/starts-with? path "/") path
                        (str/starts-with? path (str root-name "/")) (str (state-dir) "/" (subs path (inc (count root-name))))
                        :else (str (state-dir) "/" path))]
        (g/should (str/includes? (or (fs/slurp abs-path) "") content))))))

(defgiven crew-has-file #"crew \"([^\"]+)\" has file \"([^\"]+)\" with \"([^\"]+)\""
  [crew-id filename content]
  (with-feature-fs
    (fn []
      (let [quarters (str (state-dir) "/crew/" crew-id)
            path     (str quarters "/" filename)]
        (fs/mkdirs quarters)
        (fs/spit path content)))))

;; endregion ^^^^^ When ^^^^^

;; region ----- Then -----

(defthen error-contains-quoted #"the error contains \"([^\"]+)\""
  [expected]
  (g/should (str/includes? (or (g/get :error) "") expected)))

(defthen session-count-is "the session count is {int}"
  [n]
  (let [n (if (string? n) (parse-long n) n)]
    (g/should= n (count (with-feature-fs #(storage/list-sessions (state-dir)))))))

(defn- session-match-entry [entry]
  (assoc entry
         :crew (or (:crew entry) (:agent entry))
         :file (str "sessions/" (:sessionFile entry))))

(defn- transcript-match-entry [entry include-compaction-message?]
  (cond-> entry
    (and include-compaction-message? (= "compaction" (:type entry)))
    (assoc :message {:content (:summary entry)})

    (= "toolResult" (get-in entry [:message :role]))
    (update-in [:message :content]
               #(-> (or % "")
                    (str/replace #"^Error:\s*" "")
                    (str/replace #"^path outside allowed directories:.*$" "path outside allowed directories")))))

(defn- normalize-transcript-table [table]
  (let [headers (:headers table)]
    (update table :rows
            (fn [rows]
              (mapv (fn [row]
                      (let [row-map (zipmap headers row)]
                        (mapv (fn [header cell]
                                (if (and (= "message" (get row-map "type"))
                                         (= "message.content" header)
                                         (str/blank? cell))
                                  "#*"
                                  cell))
                              headers
                              row)))
                    rows)))))

(defn- transcript-match-result [table transcript]
  (let [expected-count (count (:rows table))]
    (if (= 1 expected-count)
      (let [row            (first (:rows table))
            row-map        (zipmap (:headers table) row)
            explicit-pairs (->> row-map
                                (remove (fn [[header cell]]
                                          (or (= "#index" header)
                                              (str/blank? cell))))
                                vec)
            role           (some-> (get row-map "message.role") not-empty)
            type           (some-> (get row-map "type") not-empty)
            candidates     (cond->> transcript
                             role (filter #(= role (get-in % [:message :role])))
                             type (filter #(= type (:type %)))
                             true vec)
            candidates     (if (seq candidates) candidates transcript)
            vtable         {:rows (mapv (fn [[header cell]] [header cell]) explicit-pairs)}
            result         (or (some (fn [entry]
                                       (let [match-result (match/match-object vtable entry)]
                                         (when (empty? (:failures match-result)) match-result)))
                                     candidates)
                               (match/match-object vtable (first candidates)))]
        {:captures (:captures result)
         :failures (:failures result)
         :pass?    (empty? (:failures result))})
      (let [direct (match/match-entries table transcript)]
        (if (empty? (:failures direct))
          direct
          (or (some (fn [start]
                      (let [window (subvec transcript start (min (count transcript) (+ start expected-count)))
                            result (match/match-entries table window)]
                        (when (empty? (:failures result)) result)))
                    (range (count transcript)))
              direct))))))

(defthen sessions-match "the following sessions match:"
  [table]
  (let [listing (mapv session-match-entry (with-feature-fs #(storage/list-sessions (state-dir))))
        result  (match/match-entries table listing)]
    (g/should= [] (:failures result))))

(defthen session-file-is-quoted #"the session file is \"([^\"]+)\""
  [expected-path]
  (let [entry (current-session)]
    (g/should= expected-path (str "sessions/" (:sessionFile entry)))))

(defthen most-recent-session-is "the most recent session is {string}"
  [session-name]
  (let [expected (unquote-string session-name)
        entry     (with-feature-fs #(storage/most-recent-session (state-dir)))]
    (g/should= expected (:id entry))))

(defthen agent-session-count #"agent \"([^\"]+)\" has (\d+) sessions?"
  [agent-id n]
  (let [listing (with-feature-fs #(storage/list-sessions (state-dir) agent-id))]
    (g/should= (parse-long n) (count listing))))

(defthen crew-session-count #"crew \"([^\"]+)\" has (\d+) sessions?"
  [crew-id n]
  (let [listing (with-feature-fs #(storage/list-sessions (state-dir) crew-id))]
    (g/should= (parse-long n) (count listing))))

(defthen agent-sessions-matching "agent {agent:string} has sessions matching:"
  [agent-id table]
  (let [listing (with-feature-fs #(storage/list-sessions (state-dir) agent-id))
        result  (match/match-entries table listing)]
    (g/should= [] (:failures result))))

(defthen crew-sessions-matching "crew {crew:string} has sessions matching:"
  [crew-id table]
  (let [listing (map session-match-entry (with-feature-fs #(storage/list-sessions (state-dir) crew-id)))
        result  (match/match-entries table listing)]
    (g/should= [] (:failures result))))

(defthen session-transcript-count #"session \"([^\"]+)\" has (\d+) transcript entr(?:y|ies)"
  [key-str n]
  (let [transcript (with-feature-fs #(storage/get-transcript (state-dir) key-str))]
    (g/should= (parse-long n) (count transcript))))

(defthen async-compaction-in-flight #"an async compaction for session \"([^\"]+)\" is in flight"
  [key-str]
  (await-turn!)
  (g/should (single-turn/async-compaction-in-flight? key-str)))

(defthen session-transcript-matching "session {key:string} has transcript matching:"
  "Awaits both the in-memory turn-future AND any ACP turn, then matches
   table rows against the transcript. By default skips 'session' header
   entries and uses a column-aware matcher that includes compaction
   summaries unless a 'summary' column is present. Use '#index' in any
   row to force strict positional match."
  [key-str table]
  (await-turn!)
  (await-acp-turn!)
  (let [table (normalize-transcript-table table)
        transcript (with-feature-fs #(storage/get-transcript (state-dir) key-str))
         explicit-idx? (some #(contains? % "#index") (map #(zipmap (:headers table) %) (:rows table)))
         wants-session? (some #(= "session" (get % "type")) (map #(zipmap (:headers table) %) (:rows table)))
         include-compaction-message? (not (some #{"summary"} (:headers table)))
         transcript (if (or explicit-idx? wants-session?)
                      transcript
                      (vec (remove #(= "session" (:type %)) transcript)))
         transcript   (mapv #(transcript-match-entry % include-compaction-message?) transcript)
         result     (if explicit-idx?
                      (match/match-entries table transcript)
                      (transcript-match-result table transcript))]
     (g/should= [] (:failures result))))

(defthen compaction-defaults "the compaction defaults are:"
  [table]
  (let [rows (map #(zipmap (:headers table) %) (:rows table))]
    (doseq [row rows]
      (let [window (parse-long (get row "context-window"))]
        (g/should= (parse-long (get row "threshold")) (session-compaction/default-threshold window))
        (g/should= (parse-long (get row "tail")) (session-compaction/default-tail window))))))

(defthen prompt-on-session-matches "the prompt \"{content:string}\" on session {key:string} matches:"
  "Appends a synthetic user message with the given content, rebuilds the
   prompt in-process (via loaded-config + :crew + :models atoms), and
   matches against the table. Does NOT route through production turn
   code — any hot-reload or comm-layer logic is bypassed. Use
   'the system prompt contains' after a real 'the user sends' for
   end-to-end assertions instead."
  [content key-str table]
  (g/assoc! :current-key key-str)
  (with-feature-fs
    (fn []
      (storage/append-message! (state-dir) key-str {:role "user" :content content})
      (let [transcript (storage/get-transcript (state-dir) key-str)
            session    (storage/get-session (state-dir) key-str)
            agent-id   (or (:crew session) (:agent session) "main")
            cfg        (loaded-config)
            agents     (merged-agents)
            models     (loaded-models)
            agent-cfg  (get agents agent-id)
            model-cfg  (get models (:model agent-cfg))
            tools      (g/get :tools)
            builder    (if (= "anthropic" (:provider model-cfg))
                         anthropic-prompt/build
                         prompt/build)
            ctx        (session-ctx/resolve-turn-context {:cfg    cfg
                                                          :cwd    (:cwd session)
                                                          :home   (state-dir)}
                                                         agent-id)
            soul       (if-let [boot-files (:boot-files ctx)]
                         (str (:soul ctx) "\n\n" boot-files)
                         (:soul ctx))
            p          (builder {:model          (:model model-cfg)
                                 :soul           soul
                                 :transcript     transcript
                                 :tools          tools
                                 :context-window (:context-window model-cfg)})
             result     (match/match-object table p)]
        (g/should= [] (:failures result))))))

(defthen session-index-has-keys "the session index has keys:"
  [table]
  (let [index-path (str (state-dir) "/sessions/index.edn")
        index-map  (edn/read-string (with-feature-fs #(fs/slurp index-path)))
        actual     (set (keys index-map))
        expected   (set (map first (:rows table)))]
    (g/should= expected actual)))

(defthen system-prompt-contains #"the system prompt contains \"([^\"]+)\""
  "Reads :llm-request captured by complete-turn! after a real turn
   (either 'the user sends' or 'isaac is run with'). Asserts the first
   message's content (the system prompt) contains the given substring.
   Use this for end-to-end prompt assertions — unlike
   'the prompt ... matches:', which builds synthetically."
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
  (let [entries (with-feature-fs #(storage/get-transcript (state-dir) key-str))
        role    (unquote-string role)]
    (g/should-not (some #(= role (get-in % [:message :role])) entries))))

(defthen session-has-cwd #"session \"([^\"]+)\" has cwd"
  [key-str]
  (let [session (with-feature-fs #(storage/get-session (state-dir) key-str))]
    (g/should (seq (:cwd session)))))

(defthen prompt-has-tool-count "the prompt has {int} tools"
  [n]
  (let [n (if (string? n) (parse-long n) n)]
    (g/should= n (count (prompt-tools)))))

(defthen prompt-has-tools "the prompt has tools:"
  "Reads :llm-request from complete-turn! capture. Asserts the set of
   tool names in the request equals the set in the table's first column.
   Exact set equality — use 'the prompt does not have tools:' to check
   specific exclusions."
  [table]
  (let [actual (set (map prompt-tool-name (prompt-tools)))
        expected (set (map first (:rows table)))]
    (g/should= expected actual)))

(defthen prompt-does-not-have-tools "the prompt does not have tools:"
  [table]
  (let [actual (set (map prompt-tool-name (prompt-tools)))
        disallowed (set (map first (:rows table)))]
    (g/should-not (seq (set/intersection actual disallowed)))))

(defthen prompt-messages-contain-tool-call "the prompt messages contain a tool call with:"
  "Reads :built-prompt (from 'the prompt for session X is built for
   provider Y'). Finds the first message with :tool_calls and matches
   against the table. Pair with the prompt-built-for-provider step."
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
  (with-feature-fs
    (fn []
      (let [key-str       (current-key)
            session       (storage/get-session (state-dir) key-str)
            transcript    (storage/get-transcript (state-dir) key-str)
            agent-id      (or (:crew session) (:agent session) "main")
            cfg           (loaded-config)
            model-cfg     (current-model-config)
            ctx           (session-ctx/resolve-turn-context {:cfg    cfg
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
        (g/should= [] (:failures result))))))

;; endregion ^^^^^ Then ^^^^^
