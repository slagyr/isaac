(ns isaac.session.storage
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import
    (java.util UUID)))

;; region ----- Helpers -----

(defn- new-uuid [] (str (UUID/randomUUID)))
(defn- now-ms [] (System/currentTimeMillis))
(defn- read-json [s] (json/parse-string s true))
(defn- write-json [v] (json/generate-string v))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Paths -----

(defn- sessions-dir [state-dir agent-id]
  (str state-dir "/agents/" agent-id "/sessions"))

(defn- index-path [state-dir agent-id]
  (str (sessions-dir state-dir agent-id) "/sessions.json"))

(defn- transcript-path [state-dir agent-id session-file]
  (str (sessions-dir state-dir agent-id) "/" session-file))

;; endregion ^^^^^ Paths ^^^^^

;; region ----- Index -----

(defn- read-index [state-dir agent-id]
  (let [path (index-path state-dir agent-id)
        f    (io/file path)]
    (if (.exists f)
      (read-json (slurp f))
      [])))

(defn- write-index! [state-dir agent-id entries]
  (let [path (index-path state-dir agent-id)]
    (io/make-parents path)
    (spit path (write-json entries))))

(defn- update-index-entry! [state-dir agent-id key-str updater]
  (let [entries (read-index state-dir agent-id)
        updated (mapv (fn [e]
                        (if (= (:key e) key-str)
                          (updater e)
                          e))
                      entries)]
    (write-index! state-dir agent-id updated)))

;; endregion ^^^^^ Index ^^^^^

;; region ----- Transcript -----

(defn- read-transcript [state-dir agent-id session-file]
  (let [path (transcript-path state-dir agent-id session-file)
        f    (io/file path)]
    (if (.exists f)
      (->> (str/split-lines (slurp f))
           (remove str/blank?)
           (mapv read-json))
      [])))

(defn- append-entry! [state-dir agent-id session-file entry]
  (let [path (transcript-path state-dir agent-id session-file)]
    (spit path (str (write-json entry) "\n") :append true)))

;; endregion ^^^^^ Transcript ^^^^^

;; region ----- Public API -----

(defn parse-key [key-str]
  (let [parts (str/split key-str #":")]
    (when (>= (count parts) 5)
      {:agent        (nth parts 1)
       :channel      (nth parts 2)
       :chatType     (nth parts 3)
       :conversation (nth parts 4)})))

(defn create-session!
  "Create or resume a session. If a session with the given key already exists,
   returns the existing entry. Otherwise creates a new one."
  [state-dir key-str]
  (let [{:keys [agent channel chatType]} (parse-key key-str)
        entries  (read-index state-dir agent)
        existing (first (filter #(= key-str (:key %)) entries))]
    (if existing
      existing
      (let [session-id   (new-uuid)
            session-file (str session-id ".jsonl")
            now          (now-ms)
            header       {:type "session" :id session-id :timestamp now}
            entry        {:key             key-str
                          :sessionId       session-id
                          :sessionFile     session-file
                          :updatedAt       now
                          :channel         channel
                          :chatType        chatType
                          :compactionCount 0
                          :inputTokens     0
                          :outputTokens    0
                          :totalTokens     0}]
        (write-index! state-dir agent (conj entries entry))
        (io/make-parents (transcript-path state-dir agent session-file))
        (append-entry! state-dir agent session-file header)
        entry))))

(defn list-sessions
  "List all sessions for an agent."
  [state-dir agent-id]
  (read-index state-dir agent-id))

(defn update-session!
  "Update fields on a session's index entry."
  [state-dir key-str updates]
  (let [{:keys [agent]} (parse-key key-str)]
    (update-index-entry! state-dir agent key-str
                         (fn [e] (merge e updates)))))

(defn- find-entry [state-dir key-str]
  (let [{:keys [agent]} (parse-key key-str)
        entries (read-index state-dir agent)]
    (first (filter #(= (:key %) key-str) entries))))

(defn get-transcript
  "Read the transcript for a session key."
  [state-dir key-str]
  (let [{:keys [agent]} (parse-key key-str)
        entry (find-entry state-dir key-str)]
    (when entry
      (read-transcript state-dir agent (:sessionFile entry)))))

(defn- last-entry-id [transcript]
  (:id (last transcript)))

(defn append-message!
  "Append a message entry to a session's transcript. Returns the entry."
  [state-dir key-str message]
  (let [{:keys [agent]}  (parse-key key-str)
        entry            (find-entry state-dir key-str)
        transcript       (read-transcript state-dir agent (:sessionFile entry))
        parent-id        (last-entry-id transcript)
        msg-id           (new-uuid)
        now              (now-ms)
        transcript-entry {:type      "message"
                          :id        msg-id
                          :parentId  parent-id
                          :timestamp now
                          :message   message}]
    (append-entry! state-dir agent (:sessionFile entry) transcript-entry)
    (update-index-entry! state-dir agent key-str
                         (fn [e] (cond-> (assoc e :updatedAt now)
                                   (:channel message) (assoc :lastChannel (:channel message))
                                   (:to message)      (assoc :lastTo (:to message)))))
    transcript-entry))

(defn append-compaction!
  "Append a compaction entry to a session's transcript. Returns the entry."
  [state-dir key-str {:keys [summary firstKeptEntryId tokensBefore]}]
  (let [{:keys [agent]} (parse-key key-str)
        entry           (find-entry state-dir key-str)
        transcript      (read-transcript state-dir agent (:sessionFile entry))
        parent-id       (last-entry-id transcript)
        compaction-id   (new-uuid)
        now             (now-ms)
        compaction      {:type             "compaction"
                         :id               compaction-id
                         :parentId         parent-id
                         :timestamp        now
                         :summary          summary
                         :firstKeptEntryId firstKeptEntryId
                         :tokensBefore     tokensBefore}]
    (append-entry! state-dir agent (:sessionFile entry) compaction)
    (update-index-entry! state-dir agent key-str
                         (fn [e] (-> e
                                     (assoc :updatedAt now)
                                     (update :compactionCount inc))))
    compaction))

(defn update-tokens!
  "Update token counts for a session."
  [state-dir key-str {:keys [inputTokens outputTokens]}]
  (let [{:keys [agent]} (parse-key key-str)]
    (update-index-entry! state-dir agent key-str
                         (fn [e] (-> e
                                     (update :inputTokens + inputTokens)
                                     (update :outputTokens + outputTokens)
                                     (assoc :totalTokens (+ (+ (:inputTokens e) inputTokens)
                                                            (+ (:outputTokens e) outputTokens))))))))

;; endregion ^^^^^ Public API ^^^^^
