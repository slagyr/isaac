(ns isaac.features.steps.session
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.features.matchers :as match]
    [isaac.cli.chat :as chat]
    [isaac.llm.grover :as grover]
    [isaac.prompt.anthropic :as anthropic-prompt]
    [isaac.prompt.builder :as prompt]
    [isaac.session.key :as key]
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

(defn- current-agent-config []
  (let [agents   (g/get :agents)
        key-str  (current-key)
        agent-id (:agent (storage/parse-key key-str))]
    (get agents agent-id)))

(defn- current-model-config []
  (let [models (g/get :models)
        agent  (current-agent-config)]
    (get models (:model agent))))

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
    (g/assoc! :agents (into {} (map (fn [a] [(:name a) a]) agents)))))

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
                              (get m "type")      (assoc :type (get m "type"))
                              (get m "content")   (assoc :content (get m "content"))
                              (get m "model")     (assoc :model (let [v (get m "model")] (when-not (str/blank? v) v)))
                              (get m "tool_call") (assoc :tool_call (get m "tool_call"))
                              (get m "arguments") (assoc :arguments (json/parse-string (get m "arguments") true)))))
                        (:rows table))]
    (grover/enqueue! responses)))

(defgiven llm-server-down "the LLM server is not running"
  []
  (g/assoc! :llm-error {:error :connection-refused :message "Could not connect to Ollama server"}))

;; endregion ^^^^^ Given: Infrastructure ^^^^^

;; region ----- Given: Sessions & Transcripts -----

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
                      (get row-map "updatedAt")    (assoc :updatedAt (parse-long (get row-map "updatedAt"))))]
        (when (seq updates)
          (storage/update-session! (state-dir) key-str updates)))
      (g/assoc! :current-key key-str))))

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
                                 (get row-map "message.channel")  (assoc :channel (get row-map "message.channel"))
                                 (get row-map "message.to")       (assoc :to (get row-map "message.to")))))))

(defgiven session-has-transcript "session {key:string} has transcript:"
  [key-str table]
  (g/assoc! :current-key key-str)
  (doseq [row (:rows table)]
    (let [row-map (zipmap (:headers table) row)]
      (append-transcript-entry! key-str row-map))))

;; endregion ^^^^^ Given: Sessions & Transcripts ^^^^^

;; region ----- When -----

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

(defwhen entries-appended "entries are appended to session {key:string}:"
  [key-str table]
  (g/assoc! :current-key key-str)
  (doseq [row (:rows table)]
    (let [row-map (zipmap (:headers table) row)]
      (append-transcript-entry! key-str row-map))))

(defwhen user-sends-on-session "the user sends \"{content:string}\" on session {key:string}"
  [content key-str]
  (g/assoc! :current-key key-str)
  (let [agent-cfg  (current-agent-config)
        model-cfg  (current-model-config)
        provider   (:provider model-cfg)
        send-opts  {:model          (:model model-cfg)
                    :soul           (:soul agent-cfg)
                    :provider       provider
                    :provider-config (provider-config)
                    :context-window (:contextWindow model-cfg)}]
    (let [result (atom nil)
          output (with-out-str
                   (try
                     (reset! result (@#'chat/process-user-input! (state-dir) key-str content send-opts))
                     (catch Exception e
                       (reset! result {:error :exception :message (.getMessage e)}))))]
      (g/assoc! :llm-result @result)
      (g/assoc! :output output))))

;; endregion ^^^^^ When ^^^^^

;; region ----- Then -----

(defthen agent-session-count #"agent \"([^\"]+)\" has (\d+) sessions?"
  [agent-id n]
  (let [listing (storage/list-sessions (state-dir) agent-id)]
    (g/should= (parse-long n) (count listing))))

(defthen agent-sessions-matching "agent {agent:string} has sessions matching:"
  [agent-id table]
  (let [listing (storage/list-sessions (state-dir) agent-id)
        result  (match/match-entries table listing)]
    (g/should= [] (:failures result))))

(defthen session-transcript-count #"session \"([^\"]+)\" has (\d+) transcript entr(?:y|ies)"
  [key-str n]
  (let [transcript (storage/get-transcript (state-dir) key-str)]
    (g/should= (parse-long n) (count transcript))))

(defthen session-transcript-matching "session {key:string} has transcript matching:"
  [key-str table]
  (let [transcript (storage/get-transcript (state-dir) key-str)
        result     (match/match-entries table transcript)]
    (g/should= [] (:failures result))))

(defthen prompt-on-session-matches "the prompt \"{content:string}\" on session {key:string} matches:"
  [content key-str table]
  (g/assoc! :current-key key-str)
  (storage/append-message! (state-dir) key-str {:role "user" :content content})
  (let [transcript (storage/get-transcript (state-dir) key-str)
        agent-id   (:agent (storage/parse-key key-str))
        agents     (g/get :agents)
        models     (g/get :models)
        agent-cfg  (get agents agent-id)
        model-cfg  (get models (:model agent-cfg))
        tools      (g/get :tools)
        builder    (if (= "anthropic" (:provider model-cfg))
                     anthropic-prompt/build
                     prompt/build)
        p          (builder {:model          (:model model-cfg)
                             :soul           (:soul agent-cfg)
                             :transcript     transcript
                             :tools          tools
                             :context-window (:contextWindow model-cfg)})
        result     (match/match-object table p)]
    (g/should= [] (:failures result))))

;; endregion ^^^^^ Then ^^^^^
