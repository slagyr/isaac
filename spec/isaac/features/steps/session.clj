(ns isaac.features.steps.session
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.features.matchers :as match]
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
                         :parameters  (get t "parameters")}))
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

;; endregion ^^^^^ Then ^^^^^
