(ns isaac.session.storage
  (:require
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.pprint :as pprint]
    [clojure.string :as str]
    [isaac.logger :as log]
    [isaac.fs :as fs])
  (:import
    (java.time Instant)
    (java.time ZoneOffset)
    (java.time.format DateTimeFormatter)
    (java.util UUID)))

;; region ----- Helpers -----

(def ^:private ts-formatter
  (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss"))

(def ^:private adjectives
  ["Brisk" "Clever" "Curious" "Daring" "Merry" "Quiet" "Sunny" "Tidy"])

(def ^:private nouns
  ["Badger" "Comet" "Falcon" "Harbor" "Otter" "Signal" "Spruce" "Voyage"])

(defn- new-id []
  (subs (str (UUID/randomUUID)) 0 8))

(defn- now-iso []
  (.format ts-formatter (.atOffset (Instant/now) ZoneOffset/UTC)))

(defn- ms->iso [ms]
  (.format ts-formatter (.atOffset (Instant/ofEpochMilli ms) ZoneOffset/UTC)))

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

(defn- write-edn [v]
  (binding [*print-namespace-maps* false]
    (with-out-str (pprint/pprint v))))

(defn- short-id? [id]
  (and (string? id) (boolean (re-matches #"[a-z0-9][a-z0-9-]*" id))))

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

    (and (string? id) (re-matches #"[a-f0-9]{8}" id))
    [id id-map false]

    :else
    (if-let [mapped (get id-map id)]
      [mapped id-map true]
      (let [new (new-id)]
        [new (assoc id-map id new) true]))))

(defn- normalized-parent-id [parent-id id-map]
  (cond
    (nil? parent-id)
    [nil id-map false]

    (and (string? parent-id) (re-matches #"[a-f0-9]{8}" parent-id))
    [parent-id id-map false]

    :else
    (if-let [mapped (get id-map parent-id)]
      [mapped id-map true]
      (let [new (new-id)]
        [new (assoc id-map parent-id new) true]))))

(defn- slugify [s]
  (let [slug (-> (or s "")
                 str/lower-case
                 (str/replace #"[^a-z0-9]+" "-")
                 (str/replace #"^-+|-+$" ""))]
    (if (str/blank? slug) "session" slug)))

(defn- random-name []
  (str (rand-nth adjectives) " " (rand-nth nouns)))

(declare get-session parse-key)

(defn- legacy-key? [identifier]
  (boolean (:conversation (parse-key identifier))))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Legacy Parsing -----

(defn parse-key [key-str]
  (let [key-str (if (keyword? key-str) (name key-str) key-str)
        parts   (when (string? key-str) (str/split key-str #":"))]
    (cond
      (>= (count parts) 5)
      {:agent        (nth parts 1)
       :crew         (nth parts 1)
       :channel      (nth parts 2)
       :chatType     (nth parts 3)
       :conversation (nth parts 4)}

      (= (count parts) 3)
      {:agent        (nth parts 1)
       :crew         (nth parts 1)
       :channel      "cli"
       :chatType     "direct"
       :conversation (nth parts 2)}

      :else nil)))

(defn session-id [identifier]
  (if (legacy-key? identifier)
    (slugify (:conversation (parse-key identifier)))
    (slugify identifier)))

(defn- session-name [identifier]
  (if (legacy-key? identifier)
    (:conversation (parse-key identifier))
    identifier))

(defn- entry-defaults [identifier opts]
  (let [parsed (when (legacy-key? identifier) (parse-key identifier))]
    (merge {:agent    (or (:crew opts) (:agent opts) (:crew parsed) (:agent parsed) "main")
            :crew     (or (:crew opts) (:agent opts) (:crew parsed) (:agent parsed) "main")
            :channel  (or (:channel opts) (:channel parsed))
            :chatType (or (:chatType opts) (:chatType parsed))}
           (into {} (remove (comp nil? val) opts)))))

;; endregion ^^^^^ Legacy Parsing ^^^^^

;; region ----- Paths -----

(defn- sessions-dir [state-dir]
  (str state-dir "/sessions"))

(defn- index-path [state-dir]
  (str (sessions-dir state-dir) "/index.edn"))

(defn- transcript-path [state-dir session-file]
  (str (sessions-dir state-dir) "/" session-file))

;; endregion ^^^^^ Paths ^^^^^

;; region ----- Transcript -----

(defn- read-transcript-raw [state-dir session-file]
  (let [path (transcript-path state-dir session-file)]
    (if (fs/exists? path)
      (->> (str/split-lines (fs/slurp path))
           (remove str/blank?)
           (mapv read-json))
      [])))

(defn- write-transcript! [state-dir session-file entries]
  (let [path (transcript-path state-dir session-file)]
    (fs/mkdirs (fs/parent path))
    (fs/spit path (str (str/join "\n" (map write-json entries)) "\n"))))

(defn- append-entry! [state-dir session-file entry]
  (let [path (transcript-path state-dir session-file)]
    (fs/spit path (str (write-json entry) "\n") :append true)))

(defn- normalize-transcript-entry [entry id-map]
  (let [[id id-map id-changed?]               (normalized-id (:id entry) id-map)
        [parent-id id-map parent-changed?]   (normalized-parent-id (:parentId entry) id-map)
        ts                                    (normalize-timestamp (:timestamp entry))
        ts-changed?                           (not= ts (:timestamp entry))
        base                                  (-> entry
                                                  (assoc :id id :parentId parent-id :timestamp ts))
        normalized                            (case (:type base)
                                                "session" (-> base
                                                              (assoc :version (or (:version base) 3))
                                                              (assoc :cwd (or (:cwd base) (System/getProperty "user.dir"))))
                                                "message" (update base :message normalize-message)
                                                base)]
    [normalized id-map (or id-changed? parent-changed? ts-changed? (not= normalized entry))]))

(defn- normalize-transcript [entries]
  (loop [remaining entries id-map {} out [] changed? false]
    (if (empty? remaining)
      [out changed?]
      (let [[normalized next-id-map entry-changed?] (normalize-transcript-entry (first remaining) id-map)]
        (recur (rest remaining)
               next-id-map
               (conj out normalized)
               (or changed? entry-changed?))))))

(defn- migrate-transcript! [state-dir session-file]
  (let [raw                 (read-transcript-raw state-dir session-file)
        [normalized changed?] (normalize-transcript raw)]
    (when changed?
      (write-transcript! state-dir session-file normalized))
    normalized))

;; endregion ^^^^^ Transcript ^^^^^

;; region ----- Index -----

(defn- with-session-defaults [entry]
  (let [id (or (:id entry) (:key entry))]
    (-> entry
        (assoc :id id :key (or (:key entry) id))
        (update :cwd #(or % (System/getProperty "user.dir")))
        (update :updatedAt #(or (normalize-timestamp %) (now-iso)))
        (update :compactionCount #(or % 0))
        (update :inputTokens #(or % 0))
        (update :outputTokens #(or % 0))
        (update :totalTokens #(or % 0)))))

(defn- normalize-index-store [raw]
  (cond
    (map? raw)
    (reduce-kv (fn [store key-str entry]
                 (let [id         (if (keyword? key-str) (name key-str) (str key-str))
                       entry      (if (map? entry) (keywordize-map entry) {})
                       normalized (with-session-defaults (assoc entry :id id))]
                   (assoc store id normalized)))
               {}
               raw)

    (sequential? raw)
    (reduce (fn [store entry]
              (let [entry      (if (map? entry) (keywordize-map entry) {})
                    id         (or (:id entry) (:key entry))
                    normalized (with-session-defaults (assoc entry :id id))]
                (if (str/blank? id)
                  store
                  (assoc store id normalized))))
            {}
            raw)

    :else
    {}))

(defn- read-index-store [state-dir]
  (let [path  (index-path state-dir)
        raw   (if (fs/exists? path) (edn/read-string (fs/slurp path)) {})
        store (normalize-index-store raw)]
    (doseq [entry (vals store)
            :when (and (:sessionFile entry)
                       (fs/exists? (transcript-path state-dir (:sessionFile entry))))]
      (migrate-transcript! state-dir (:sessionFile entry)))
    store))

(defn- write-index-store! [state-dir store]
  (let [path (index-path state-dir)]
    (fs/mkdirs (fs/parent path))
    (fs/spit path (write-edn store))))

(defn- resolve-entry-id [store identifier]
  (cond
    (nil? identifier) nil
    (contains? store identifier) identifier
    (legacy-key? identifier) (let [id (session-id identifier)] (when (contains? store id) id))
    :else (let [id (session-id identifier)] (when (contains? store id) id))))

(defn- update-index-entry! [state-dir identifier updater]
  (let [store (read-index-store state-dir)]
    (when-let [id (resolve-entry-id store identifier)]
      (write-index-store! state-dir (assoc store id (updater (get store id)))))))

;; endregion ^^^^^ Index ^^^^^

;; region ----- Public API -----

(defn create-session!
  ([state-dir identifier]
   (create-session! state-dir identifier {}))
  ([state-dir identifier opts]
   (let [name      (or (session-name identifier) (random-name))
        id        (session-id name)
        opts      (entry-defaults identifier opts)
        store     (read-index-store state-dir)
        existing  (get store id)
        transcript-exists? (when (and existing (:sessionFile existing))
                             (fs/exists? (transcript-path state-dir (:sessionFile existing))))]
     (cond
       (and existing transcript-exists? (legacy-key? identifier))
       (do
         (log/info :session/opened :sessionId id)
         existing)

       (and existing (not (legacy-key? identifier)))
       (throw (ex-info (str "session already exists: " id) {:code -32602 :sessionId id}))

       :else
       (let [session-file (str id ".jsonl")
             now          (or (normalize-timestamp (:updatedAt opts)) (now-iso))
             transcript-id (new-id)
             header       {:type      "session"
                           :id        transcript-id
                           :timestamp now
                           :version   3
                           :cwd       (System/getProperty "user.dir")}
             entry        (with-session-defaults
                            {:id              id
                             :key             (if (legacy-key? identifier) identifier id)
                             :name            name
                             :sessionId       transcript-id
                             :sessionFile     session-file
                             :updatedAt       now
                             :cwd             (or (:cwd opts) (System/getProperty "user.dir"))
                             :agent           (:agent opts)
                             :crew            (:crew opts)
                             :channel         (:channel opts)
                             :chatType        (:chatType opts)
                             :compactionCount 0
                             :inputTokens     0
                             :outputTokens    0
                             :totalTokens     0})]
         (write-index-store! state-dir (assoc store id entry))
         (write-transcript! state-dir session-file [header])
         (log/info :session/created :sessionId id)
         entry)))))

(defn open-session [state-dir identifier]
  (when-let [entry (get-session state-dir identifier)]
    (log/info :session/opened :sessionId (:id entry))
    entry))

(defn list-agents [state-dir]
  (->> (vals (read-index-store state-dir))
       (map #(or (:crew %) (:agent %)))
       (remove str/blank?)
       distinct
       sort
       vec))

(defn list-sessions
  ([state-dir]
   (->> (vals (read-index-store state-dir))
        (sort-by :id)
        vec))
  ([state-dir agent-id]
   (->> (list-sessions state-dir)
        (filter #(= agent-id (or (:crew %) (:agent %))))
        vec)))

(defn most-recent-session
  ([state-dir]
   (->> (list-sessions state-dir)
        (sort-by :updatedAt)
        last))
  ([state-dir agent-id]
   (->> (list-sessions state-dir agent-id)
        (sort-by :updatedAt)
        last)))

(defn update-session! [state-dir identifier updates]
  (update-index-entry! state-dir identifier
                       (fn [entry]
                         (-> (merge entry updates)
                             (assoc :key (:id entry))
                             (update :updatedAt normalize-timestamp)))))

(defn get-session [state-dir identifier]
  (let [store (read-index-store state-dir)]
    (when-let [id (resolve-entry-id store identifier)]
      (get store id))))

(defn get-transcript [state-dir identifier]
  (when-let [entry (get-session state-dir identifier)]
    (migrate-transcript! state-dir (:sessionFile entry))))

(defn- last-entry-id [transcript]
  (:id (last transcript)))

(defn append-message! [state-dir identifier message]
  (let [entry             (get-session state-dir identifier)
        transcript        (get-transcript state-dir identifier)
        parent-id         (last-entry-id transcript)
        msg-id            (new-id)
        now               (now-iso)
        resolved-agent    (or (:crew message) (:agent message)
                              (when (#{"assistant" "error" "toolResult"} (:role message)) (or (:crew entry) (:agent entry)))
                              (when (= "assistant" (:role message)) "main"))
        normalized-msg    (normalize-message (cond-> message
                                               resolved-agent (assoc :agent resolved-agent :crew resolved-agent)))
        transcript-entry  {:type      "message"
                           :id        msg-id
                           :parentId  parent-id
                           :timestamp now
                           :message   normalized-msg}]
    (append-entry! state-dir (:sessionFile entry) transcript-entry)
    (update-index-entry! state-dir identifier
                         (fn [e]
                           (cond-> (assoc e :updatedAt now)
                             (:channel message) (assoc :lastChannel (:channel message))
                              (:to message)      (assoc :lastTo (:to message))
                              resolved-agent     (assoc :agent resolved-agent :crew resolved-agent))))
    transcript-entry))

(defn append-error! [state-dir identifier error-entry]
  (let [entry            (get-session state-dir identifier)
        transcript       (get-transcript state-dir identifier)
        parent-id        (last-entry-id transcript)
        error-id         (new-id)
        now              (now-iso)
        transcript-entry {:type      "error"
                          :id        error-id
                          :parentId  parent-id
                          :timestamp now
                          :content   (:content error-entry)
                          :error     (:error error-entry)
                          :model     (:model error-entry)
                          :provider  (:provider error-entry)}]
    (append-entry! state-dir (:sessionFile entry) transcript-entry)
    (update-index-entry! state-dir identifier #(assoc % :updatedAt now))
    transcript-entry))

(defn append-compaction! [state-dir identifier {:keys [summary firstKeptEntryId tokensBefore]}]
  (let [entry         (get-session state-dir identifier)
        transcript    (get-transcript state-dir identifier)
        parent-id     (last-entry-id transcript)
        compaction-id (new-id)
        now           (now-iso)
        compaction    {:type             "compaction"
                       :id               compaction-id
                       :parentId         parent-id
                       :timestamp        now
                       :summary          summary
                       :firstKeptEntryId firstKeptEntryId
                       :tokensBefore     tokensBefore}]
    (append-entry! state-dir (:sessionFile entry) compaction)
    (update-index-entry! state-dir identifier
                         (fn [e] (-> e
                                     (assoc :updatedAt now)
                                     (update :compactionCount inc))))
    compaction))

(defn truncate-after-compaction! [state-dir identifier]
  (let [entry         (get-session state-dir identifier)
        transcript    (read-transcript-raw state-dir (:sessionFile entry))
        compaction    (->> transcript (filter #(= "compaction" (:type %))) last)]
    (when compaction
      (let [first-kept-id  (:firstKeptEntryId compaction)
            compaction-id  (:id compaction)
            removed-ids    (loop [remaining transcript ids #{}]
                             (if (empty? remaining)
                               ids
                               (let [e (first remaining)]
                                 (cond
                                   (= (:id e) compaction-id) ids
                                   (and first-kept-id (= (:id e) first-kept-id)) ids
                                   (= "message" (:type e)) (recur (rest remaining) (conj ids (:id e)))
                                   :else (recur (rest remaining) ids)))))
            remap          (loop [remaining transcript last-kept nil mapping {}]
                             (if (empty? remaining)
                               mapping
                               (let [e (first remaining)]
                                 (if (contains? removed-ids (:id e))
                                   (recur (rest remaining) last-kept (assoc mapping (:id e) last-kept))
                                   (recur (rest remaining) (:id e) mapping)))))
            new-transcript (into []
                                (keep (fn [e]
                                        (when-not (contains? removed-ids (:id e))
                                          (if-let [new-parent (get remap (:parentId e))]
                                            (assoc e :parentId new-parent)
                                            e))))
                                transcript)]
        (when (pos? (count removed-ids))
          (write-transcript! state-dir (:sessionFile entry) new-transcript)
          (count removed-ids))))))

(defn update-tokens! [state-dir identifier {:keys [inputTokens outputTokens cacheRead cacheWrite]}]
  (update-index-entry! state-dir identifier
                       (fn [entry]
                         (cond-> (-> entry
                                     (update :inputTokens + (or inputTokens 0))
                                     (update :outputTokens + (or outputTokens 0))
                                     (assoc :totalTokens (+ (+ (:inputTokens entry) (or inputTokens 0))
                                                            (+ (:outputTokens entry) (or outputTokens 0)))))
                           cacheRead  (update :cacheRead (fnil + 0) cacheRead)
                           cacheWrite (update :cacheWrite (fnil + 0) cacheWrite)))))

;; endregion ^^^^^ Public API ^^^^^
