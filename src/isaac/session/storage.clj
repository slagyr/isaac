(ns isaac.session.storage
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [isaac.logger :as log])
  (:import
    (java.time Instant)
    (java.time ZoneOffset)
    (java.time.format DateTimeFormatter)
    (java.util UUID)))

(declare parse-key)

;; region ----- Helpers -----

(def ^:private ts-formatter
  (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss"))

(defn- new-id []
  (subs (str (UUID/randomUUID)) 0 8))

(defn- now-iso []
  (.format ts-formatter (.atOffset (Instant/now) ZoneOffset/UTC)))

(defn- ms->iso [ms]
  (.format ts-formatter (.atOffset (Instant/ofEpochMilli ms) ZoneOffset/UTC)))

(defn- short-id? [id]
  (and (string? id) (boolean (re-matches #"[a-f0-9]{8}" id))))

(defn- parse-long-safe [s]
  (try
    (when (string? s) (Long/parseLong s))
    (catch Exception _ nil)))

(defn- normalize-timestamp [ts]
  (cond
    (number? ts) (ms->iso ts)
    (string? ts) (if-let [n (parse-long-safe ts)]
                   (ms->iso n)
                   ts)
    :else        ts))

(defn- read-json [s] (json/parse-string s true))
(defn- write-json [v] (json/generate-string v))

(defn- keywordize-map [m]
  (into {} (map (fn [[k v]] [(if (keyword? k) k (keyword k)) v]) m)))

(defn- text-blocks? [content]
  (and (vector? content)
       (every? map? content)
       (every? #(contains? % :type) content)))

(def text-content-roles #{"user"})

(defn- normalize-message-content [role content]
  (if (contains? text-content-roles role)
    (cond
      (string? content) [{:type "text" :text content}]
      (text-blocks? content) content
      :else content)
    content))

(defn- normalize-message [message]
  (let [role (:role message)]
    (cond-> (assoc message :content (normalize-message-content role (:content message)))
      (keyword? (:error message)) (update :error str))))

(defn- normalized-id [id id-map]
  (cond
    (nil? id)
    (let [new (new-id)]
      [new id-map true])

    (short-id? id)
    [id id-map false]

    :else
    (if-let [mapped (get id-map id)]
      [mapped id-map true]
      (let [new-id (new-id)]
        [new-id (assoc id-map id new-id) true]))))

(defn- normalized-parent-id [parent-id id-map]
  (cond
    (nil? parent-id)
    [nil id-map false]

    (short-id? parent-id)
    [parent-id id-map false]

    :else
    (if-let [mapped (get id-map parent-id)]
      [mapped id-map true]
      (let [new-id (new-id)]
        [new-id (assoc id-map parent-id new-id) true]))))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Paths -----

(defn- sessions-dir [state-dir agent-id]
  (str state-dir "/agents/" agent-id "/sessions"))

(defn- index-path [state-dir agent-id]
  (str (sessions-dir state-dir agent-id) "/sessions.json"))

(defn- transcript-path [state-dir agent-id session-file]
  (str (sessions-dir state-dir agent-id) "/" session-file))

;; endregion ^^^^^ Paths ^^^^^

(declare parse-key)

;; region ----- Transcript -----

(defn- read-transcript-raw [state-dir agent-id session-file]
  (let [path (transcript-path state-dir agent-id session-file)
        f    (io/file path)]
    (if (.exists f)
      (->> (str/split-lines (slurp f))
           (remove str/blank?)
           (mapv read-json))
      [])))

(defn- write-transcript! [state-dir agent-id session-file entries]
  (let [path (transcript-path state-dir agent-id session-file)]
    (io/make-parents path)
    (spit path (str (str/join "\n" (map write-json entries)) "\n"))))

(defn- append-entry! [state-dir agent-id session-file entry]
  (let [path (transcript-path state-dir agent-id session-file)]
    (spit path (str (write-json entry) "\n") :append true)))

(defn- normalize-transcript-entry [entry id-map]
  (let [[id id-map id-changed?]          (normalized-id (:id entry) id-map)
        [parent-id id-map parent-changed?] (normalized-parent-id (:parentId entry) id-map)
        ts                               (normalize-timestamp (:timestamp entry))
        ts-changed?                      (not= ts (:timestamp entry))
        base                             (-> entry
                                             (assoc :id id :parentId parent-id :timestamp ts))
        normalized                       (case (:type base)
                                         "session" (-> base
                                                       (assoc :version (or (:version base) 3))
                                                       (assoc :cwd (or (:cwd base) (System/getProperty "user.dir"))))
                                         "message" (update base :message normalize-message)
                                         base)]
    [normalized id-map (or id-changed? parent-changed? ts-changed? (not= normalized entry))]))

(defn- normalize-transcript [entries]
  (loop [remaining entries
         id-map    {}
         out       []
         changed?  false]
    (if (empty? remaining)
      [out changed?]
      (let [[normalized next-id-map entry-changed?] (normalize-transcript-entry (first remaining) id-map)]
        (recur (rest remaining)
               next-id-map
               (conj out normalized)
               (or changed? entry-changed?))))))

(defn- migrate-transcript! [state-dir agent-id session-file]
  (let [raw (read-transcript-raw state-dir agent-id session-file)
        [normalized changed?] (normalize-transcript raw)]
    (when changed?
      (write-transcript! state-dir agent-id session-file normalized))
    normalized))

;; endregion ^^^^^ Transcript ^^^^^

;; region ----- Index -----

(defn- with-session-defaults [entry]
  (let [parsed (parse-key (:key entry))]
    (-> entry
        (update :updatedAt #(or (normalize-timestamp %) (now-iso)))
        (update :compactionCount #(or % 0))
        (update :inputTokens #(or % 0))
        (update :outputTokens #(or % 0))
        (update :totalTokens #(or % 0))
        (update :channel #(or % (:channel parsed)))
        (update :chatType #(or % (:chatType parsed))))))

(defn- newer-entry [a b]
  (let [ta (or (:updatedAt a) "")
        tb (or (:updatedAt b) "")]
    (if (pos? (compare ta tb)) a b)))

(defn- normalize-index-store [raw]
  (cond
    (sequential? raw)
    (reduce (fn [[store changed?] entry]
              (let [entry   (if (map? entry) (keywordize-map entry) {})
                    key-str (:key entry)]
                (if (str/blank? key-str)
                  [store true]
                  (let [normalized (with-session-defaults entry)
                        existing   (get store key-str)
                        chosen     (if existing (newer-entry existing normalized) normalized)]
                    [(assoc store key-str (assoc chosen :key key-str))
                     (or changed? (not= normalized entry) (some? existing))]))))
            [{} false]
            raw)

    (map? raw)
    (reduce-kv (fn [[store changed?] key-str entry]
                 (let [key-str    (if (keyword? key-str) (name key-str) (str key-str))
                       entry      (if (map? entry) (keywordize-map entry) {})
                       normalized (with-session-defaults (assoc entry :key key-str))]
                   [(assoc store key-str normalized)
                    (or changed?
                        (not (map? entry))
                        (not= (if (keyword? (:key entry)) (name (:key entry)) (:key entry)) key-str)
                        (not= normalized (assoc entry :key key-str)))]))
               [{} false]
               raw)

    :else
    [{} true]))

(defn- read-index-store [state-dir agent-id]
  (let [path       (index-path state-dir agent-id)
        file       (io/file path)
        raw        (if (.exists file) (json/parse-string (slurp file) false) {})
        [store changed?] (normalize-index-store raw)]
    (when (or changed? (and (.exists file) (sequential? raw)))
      (io/make-parents path)
      (spit path (write-json store)))
    (doseq [entry (vals store)
            :when (and (:sessionFile entry)
                       (.exists (io/file (transcript-path state-dir agent-id (:sessionFile entry)))))]
      (migrate-transcript! state-dir agent-id (:sessionFile entry)))
    store))

(defn- write-index-store! [state-dir agent-id store]
  (let [path (index-path state-dir agent-id)]
    (io/make-parents path)
    (spit path (write-json store))))

(defn- update-index-entry! [state-dir agent-id key-str updater]
  (let [store (read-index-store state-dir agent-id)]
    (when-let [entry (get store key-str)]
      (write-index-store! state-dir agent-id
                          (assoc store key-str (updater entry))))))

;; endregion ^^^^^ Index ^^^^^

;; region ----- Public API -----

(defn parse-key [key-str]
  (let [key-str (if (keyword? key-str) (name key-str) key-str)
        parts   (when (string? key-str) (str/split key-str #":"))]
    (cond
      (>= (count parts) 5)
      {:agent        (nth parts 1)
       :channel      (nth parts 2)
       :chatType     (nth parts 3)
       :conversation (nth parts 4)}

      (= (count parts) 3)
      {:agent        (nth parts 1)
       :channel      "cli"
       :chatType     "direct"
       :conversation (nth parts 2)}

      :else nil)))

(defn create-session!
  "Create or resume a session. If a session with the given key already exists
   and its transcript file is present, returns the existing entry. Otherwise
   creates a new one."
  [state-dir key-str]
  (let [{:keys [agent channel chatType]} (parse-key key-str)
        store               (read-index-store state-dir agent)
        existing            (get store key-str)
        transcript-exists?  (when (and existing (not (str/blank? (:sessionFile existing))))
                              (.exists (io/file (transcript-path state-dir agent (:sessionFile existing)))))]
    (if transcript-exists?
      (do (log/info :session/resumed :key key-str)
          existing)
      (let [session-id   (new-id)
            session-file (str session-id ".jsonl")
            now          (now-iso)
            header       {:type "session"
                          :id session-id
                          :timestamp now
                          :version 3
                          :cwd (System/getProperty "user.dir")}
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
        (write-index-store! state-dir agent (assoc store key-str entry))
        (io/make-parents (transcript-path state-dir agent session-file))
        (append-entry! state-dir agent session-file header)
        (log/info :session/created :key key-str)
        entry))))

(defn list-sessions
  "List all sessions for an agent."
  [state-dir agent-id]
  (->> (vals (read-index-store state-dir agent-id))
       (sort-by :key)
       vec))

(defn update-session!
  "Update fields on a session's index entry."
  [state-dir key-str updates]
  (let [{:keys [agent]} (parse-key key-str)]
    (update-index-entry! state-dir agent key-str
                         (fn [e]
                           (-> (merge e updates)
                               (update :updatedAt normalize-timestamp))))))

(defn- find-entry [state-dir key-str]
  (let [{:keys [agent]} (parse-key key-str)]
    (get (read-index-store state-dir agent) key-str)))

(defn get-session
  "Return the index entry for a session key, or nil if not found."
  [state-dir key-str]
  (find-entry state-dir key-str))

(defn get-transcript
  "Read the transcript for a session key."
  [state-dir key-str]
  (let [{:keys [agent]} (parse-key key-str)
        entry           (find-entry state-dir key-str)]
    (when entry
      (migrate-transcript! state-dir agent (:sessionFile entry)))))

(defn- last-entry-id [transcript]
  (:id (last transcript)))

(defn append-message!
  "Append a message entry to a session's transcript. Returns the entry."
  [state-dir key-str message]
  (let [{:keys [agent]}  (parse-key key-str)
        entry            (find-entry state-dir key-str)
        transcript       (get-transcript state-dir key-str)
        parent-id        (last-entry-id transcript)
        msg-id           (new-id)
        now              (now-iso)
        transcript-entry {:type      "message"
                          :id        msg-id
                          :parentId  parent-id
                          :timestamp now
                          :message   (normalize-message message)}]
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
        transcript      (get-transcript state-dir key-str)
        parent-id       (last-entry-id transcript)
        compaction-id   (new-id)
        now             (now-iso)
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

(defn truncate-after-compaction!
  "Rewrite the transcript after compaction to remove summarized message entries.
   Removes all message entries that appear before the last compaction's firstKeptEntryId
   (or all messages before the compaction when firstKeptEntryId is nil).
   Non-message entries are always preserved. Reparents kept entries to the nearest
   kept ancestor. Returns the count of removed entries, or nil if nothing was removed."
  [state-dir key-str]
  (let [{:keys [agent]} (parse-key key-str)
        entry           (find-entry state-dir key-str)
        transcript      (read-transcript-raw state-dir agent (:sessionFile entry))
        compaction      (->> transcript (filter #(= "compaction" (:type %))) last)]
    (when compaction
      (let [first-kept-id (:firstKeptEntryId compaction)
            compaction-id (:id compaction)
            removed-ids   (loop [remaining transcript ids #{}]
                            (if (empty? remaining)
                              ids
                              (let [e (first remaining)]
                                (cond
                                  (= (:id e) compaction-id)
                                  ids
                                  (and first-kept-id (= (:id e) first-kept-id))
                                  ids
                                  (= "message" (:type e))
                                  (recur (rest remaining) (conj ids (:id e)))
                                  :else
                                  (recur (rest remaining) ids)))))
            remap         (loop [remaining transcript last-kept nil m {}]
                            (if (empty? remaining)
                              m
                              (let [e (first remaining)]
                                (if (contains? removed-ids (:id e))
                                  (recur (rest remaining) last-kept (assoc m (:id e) last-kept))
                                  (recur (rest remaining) (:id e) m)))))
            new-transcript (into [] (keep (fn [e]
                                            (when-not (contains? removed-ids (:id e))
                                              (if-let [new-parent (get remap (:parentId e))]
                                                (assoc e :parentId new-parent)
                                                e)))
                                          transcript))]
        (when (pos? (count removed-ids))
          (write-transcript! state-dir agent (:sessionFile entry) new-transcript)
          (count removed-ids))))))

(defn update-tokens!
  "Update token counts for a session."
  [state-dir key-str {:keys [inputTokens outputTokens cacheRead cacheWrite]}]
  (let [{:keys [agent]} (parse-key key-str)]
    (update-index-entry! state-dir agent key-str
                         (fn [e] (cond-> (-> e
                                             (update :inputTokens + (or inputTokens 0))
                                             (update :outputTokens + (or outputTokens 0))
                                             (assoc :totalTokens (+ (+ (:inputTokens e) (or inputTokens 0))
                                                                    (+ (:outputTokens e) (or outputTokens 0)))))
                                   cacheRead  (update :cacheRead (fnil + 0) cacheRead)
                                   cacheWrite (update :cacheWrite (fnil + 0) cacheWrite))))))

;; endregion ^^^^^ Public API ^^^^^
