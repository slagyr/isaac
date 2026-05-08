;; mutation-tested: 2026-05-06
(ns isaac.session.storage
  (:require
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [clojure.pprint :as pprint]
    [clojure.set :as set]
    [clojure.string :as str]
    [isaac.fs :as fs]
    [isaac.logger :as log]
    [isaac.session.naming :as naming])
  (:import
    (java.time Instant)
    (java.time ZoneOffset)
    (java.time.format DateTimeFormatter)
    (java.util UUID)))

;; region ----- Helpers -----

(def ^:private ts-formatter
  (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss"))

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

(defn- keywordize-map [m]
  (into {} (map (fn [[k v]] [(if (keyword? k) k (keyword k)) v]) m)))

(def ^:private session-entry-keys
   [:compaction-count
    :compaction-disabled
    :input-tokens
    :last-input-tokens
    :last-channel
    :last-to
    :output-tokens
   :session-file
   :total-tokens
   :updated-at])

(defn- legacy-session-entry-key [kebab-key]
  (let [[head & tail] (str/split (name kebab-key) #"-")]
    (keyword (apply str head (map str/capitalize tail)))))

(defn- normalize-session-entry-keys [entry]
  (reduce (fn [result kebab-key]
            (let [legacy-key (legacy-session-entry-key kebab-key)]
              (cond
                (contains? result kebab-key)
                result

                (contains? result legacy-key)
                (-> result
                    (assoc kebab-key (get result legacy-key))
                    (dissoc legacy-key))

                :else
                result)))
          entry
          session-entry-keys))

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

(declare get-session session-id sessions-dir)

(defn session-id [identifier]
  (slugify identifier))

(defn- entry-defaults [opts]
  (merge {:crew     (or (:crew opts) "main")
          :channel  (:channel opts)
          :chatType (:chatType opts)}
         (into {} (remove (comp nil? val) opts))))

;; endregion ^^^^^ Helpers ^^^^^

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
  (let [entry (normalize-session-entry-keys entry)
        id    (or (:id entry) (:key entry))]
    (-> entry
        (assoc :id id :key (or (:key entry) id))
        (update :origin #(or % {:kind :cli}))
        (update :cwd #(or % (System/getProperty "user.dir")))
        (update :updated-at #(or (normalize-timestamp %) (now-iso)))
        (update :compaction-disabled #(if (nil? %) false %))
        (update :compaction-count #(or % 0))
        (update :input-tokens #(or % 0))
        (update :last-input-tokens #(or % 0))
        (update :output-tokens #(or % 0))
        (update :total-tokens #(or % 0)))))

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
            :when (and (:session-file entry)
                       (fs/exists? (transcript-path state-dir (:session-file entry))))]
      (migrate-transcript! state-dir (:session-file entry)))
    store))

(defn- write-index-store! [state-dir store]
  (let [path (index-path state-dir)]
    (fs/mkdirs (fs/parent path))
    (fs/spit path (write-edn store))))

(defn- resolve-entry-id [store identifier]
  (cond
    (nil? identifier) nil
    (contains? store identifier) identifier
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
   (let [opts      (-> (entry-defaults opts)
                        normalize-session-entry-keys)
           store     (read-index-store state-dir)
           name      (or identifier (naming/generate (naming/strategy state-dir) {:state-dir state-dir :store store}))
           id        (session-id name)
           existing  (get store id)
           transcript-exists? (when (and existing (:session-file existing))
                                (fs/exists? (transcript-path state-dir (:session-file existing))))]
     (cond
       (and existing transcript-exists?)
       (do
         (log/info :session/opened :sessionId id)
         existing)

       :else
       (let [session-file (str id ".jsonl")
              now          (or (normalize-timestamp (:updated-at opts)) (now-iso))
             transcript-id (new-id)
              header       {:type      "session"
                            :id        transcript-id
                            :timestamp now
                            :version   3
                            :cwd       (System/getProperty "user.dir")}
              entry        (with-session-defaults
                             {:id               id
                              :key              id
                              :name             name
                              :sessionId        transcript-id
                              :session-file     session-file
                              :origin           (:origin opts)
                              :createdAt        now
                              :updated-at       now
                              :cwd              (or (:cwd opts) (System/getProperty "user.dir"))
                              :crew             (:crew opts)
                              :channel          (:channel opts)
                              :chatType         (:chatType opts)
                              :compaction-count 0
                              :input-tokens     0
                              :last-input-tokens 0
                              :output-tokens    0
                              :total-tokens     0})]
          (write-index-store! state-dir (assoc store id entry))
          (write-transcript! state-dir session-file [header])
          (log/info :session/created :sessionId id)
         entry)))))

(defn open-session [state-dir identifier]
  (when-let [entry (get-session state-dir identifier)]
    (log/info :session/opened :sessionId (:id entry))
    entry))

(defn list-sessions
  ([state-dir]
   (->> (vals (read-index-store state-dir))
        (sort-by :id)
        vec))
  ([state-dir crew-id]
   (->> (list-sessions state-dir)
         (filter #(= crew-id (:crew %)))
         vec)))

(defn most-recent-session
  ([state-dir]
   (->> (list-sessions state-dir)
        (sort-by :updated-at)
        last))
  ([state-dir crew-id]
   (->> (list-sessions state-dir crew-id)
        (sort-by :updated-at)
        last)))

(defn update-session! [state-dir identifier updates]
  (update-index-entry! state-dir identifier
                        (fn [entry]
                          (let [updates (normalize-session-entry-keys updates)
                                updates (if-let [compaction (:compaction updates)]
                                          (assoc updates :compaction (merge (or (:compaction entry) {}) compaction))
                                          updates)]
                            (-> (merge entry updates)
                                (assoc :key (:id entry))
                                (update :updated-at normalize-timestamp))))))

(defn get-session [state-dir identifier]
  (let [store (read-index-store state-dir)]
    (when-let [id (resolve-entry-id store identifier)]
      (get store id))))

(defn- entry-toolcall-ids [entry]
  (let [message (get entry :message)
        content (:content message)]
    (cond
      (= "toolCall" (:type message))
      (keep :id [message])

      (sequential? content)
      (->> content
           (filter #(= "toolCall" (:type %)))
           (keep :id))

      :else
      nil)))

(defn- drop-orphan-toolcalls [transcript]
  (let [tool-call-ids   (->> transcript
                             (filter #(= "message" (:type %)))
                             (mapcat entry-toolcall-ids)
                             set)
        tool-result-ids (->> transcript
                             (filter #(= "toolResult" (get-in % [:message :role])))
                             (keep #(or (get-in % [:message :toolCallId])
                                        (get-in % [:message :id])
                                        (:id %)))
                             set)
        orphans         (set/difference tool-call-ids tool-result-ids)]
    (if (empty? orphans)
      transcript
      (let [remove?    (fn [entry]
                         (and (= "message" (:type entry))
                              (seq (set/intersection orphans (set (entry-toolcall-ids entry))))))
            removed-ids (->> transcript (filter remove?) (map :id) set)
            kept        (vec (remove remove? transcript))
            remap       (loop [remaining transcript last-kept nil mapping {}]
                          (if (empty? remaining)
                            mapping
                            (let [e (first remaining)]
                              (if (contains? removed-ids (:id e))
                                (recur (rest remaining) last-kept (assoc mapping (:id e) last-kept))
                                (recur (rest remaining) (:id e) mapping)))))]
        (mapv (fn [entry]
                (if-let [new-parent (get remap (:parentId entry))]
                  (assoc entry :parentId new-parent)
                  entry))
              kept)))))

(defn get-transcript [state-dir identifier]
  (when-let [entry (get-session state-dir identifier)]
    (migrate-transcript! state-dir (:session-file entry))))

(defn delete-session! [state-dir identifier]
  (let [store (read-index-store state-dir)]
    (when-let [id (resolve-entry-id store identifier)]
      (let [entry (get store id)
            path  (transcript-path state-dir (:session-file entry))]
        (write-index-store! state-dir (dissoc store id))
        (when (fs/exists? path)
          (fs/delete path))
        true))))

(defn- last-entry-id [transcript]
  (:id (last transcript)))

(defn append-message! [state-dir identifier message]
  (let [entry             (get-session state-dir identifier)
        transcript        (get-transcript state-dir identifier)
        parent-id         (last-entry-id transcript)
        msg-id            (new-id)
        now               (now-iso)
        resolved-agent    (or (:crew message)
                              (when (#{"assistant" "error" "toolResult"} (:role message)) (:crew entry))
                              (when (= "assistant" (:role message)) "main"))
        normalized-msg    (normalize-message (cond-> message
                                               resolved-agent (assoc :crew resolved-agent)))
        transcript-entry  {:type      "message"
                           :id        msg-id
                           :parentId  parent-id
                           :timestamp now
                           :message   normalized-msg}
        transcript-entry  (cond-> transcript-entry
                            (:tokens message) (assoc :tokens (:tokens message)))]
    (append-entry! state-dir (:session-file entry) transcript-entry)
    (update-index-entry! state-dir identifier
                         (fn [e]
                           (cond-> (assoc e :updated-at now)
                             (:channel message) (assoc :last-channel (:channel message))
                              (:to message)      (assoc :last-to (:to message))
                               resolved-agent     (assoc :crew resolved-agent))))
    transcript-entry))

(defn append-error! [state-dir identifier error-entry]
  (let [entry            (get-session state-dir identifier)
        transcript       (get-transcript state-dir identifier)
        parent-id        (last-entry-id transcript)
        error-id         (new-id)
        now              (now-iso)
        transcript-entry (cond-> {:type      "error"
                                    :id        error-id
                                    :parentId  parent-id
                                    :timestamp now
                                    :content   (:content error-entry)
                                    :error     (:error error-entry)
                                    :model     (:model error-entry)
                                    :provider  (:provider error-entry)}
                           (:ex-class error-entry) (assoc :ex-class (:ex-class error-entry)))]
    (append-entry! state-dir (:session-file entry) transcript-entry)
    (update-index-entry! state-dir identifier #(assoc % :updated-at now))
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
    (append-entry! state-dir (:session-file entry) compaction)
    (update-index-entry! state-dir identifier
                         (fn [e] (-> e
                                     (assoc :updated-at now)
     (update :compaction-count inc))))
     compaction))

(def ^:private bak-ts-formatter
  (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss.SSS"))

(defn- session-base [session-file]
  (subs session-file 0 (- (count session-file) (count ".jsonl"))))

(defn- backup-transcript! [state-dir session-file]
  (let [path     (transcript-path state-dir session-file)
        dir      (sessions-dir state-dir)
        base     (session-base session-file)
        ts       (.format bak-ts-formatter (.atOffset (Instant/now) ZoneOffset/UTC))
        bak-path (str dir "/" base "." ts ".bak.jsonl")]
    (when (fs/exists? path)
      (fs/spit bak-path (fs/slurp path))
      (let [backups   (->> (fs/children dir)
                           (filter #(and (str/starts-with? % (str base "."))
                                         (str/ends-with? % ".bak.jsonl")))
                           sort
                           reverse
                           (drop 8))]
        (doseq [name backups]
          (fs/delete (str dir "/" name)))))))

(defn splice-compaction! [state-dir identifier {:keys [compactedEntryIds firstKeptEntryId summary tokensBefore]}]
  (let [entry            (get-session state-dir identifier)
         transcript       (get-transcript state-dir identifier)
         compacted-ids    (set compactedEntryIds)
         removable-ids    (->> transcript
                               (filter #(and (= "message" (:type %))
                                             (contains? compacted-ids (:id %))))
                               (map :id)
                               set)
         insert-at        (or (some (fn [[idx transcript-entry]]
                                      (when (contains? removable-ids (:id transcript-entry)) idx))
                                    (map-indexed vector transcript))
                               (count transcript))
         first-kept-index (when firstKeptEntryId
                            (some (fn [[idx transcript-entry]]
                                    (when (= firstKeptEntryId (:id transcript-entry)) idx))
                                  (map-indexed vector transcript)))
         before           (subvec transcript 0 insert-at)
         compaction-id    (new-id)
         now              (now-iso)
         compaction-entry {:type             "compaction"
                           :id               compaction-id
                          :parentId         (:id (last before))
                           :timestamp        now
                           :summary          summary
                           :firstKeptEntryId firstKeptEntryId
                           :tokensBefore     tokensBefore}
         after            (->> (subvec transcript (or first-kept-index (count transcript)))
                               (remove #(contains? removable-ids (:id %)))
                               (mapv (fn [transcript-entry]
                                       (if (contains? removable-ids (:parentId transcript-entry))
                                         (assoc transcript-entry :parentId compaction-id)
                                         transcript-entry))))
         new-transcript   (drop-orphan-toolcalls (into before (cons compaction-entry after)))]
    (backup-transcript! state-dir (:session-file entry))
    (write-transcript! state-dir (:session-file entry) new-transcript)
    (update-index-entry! state-dir identifier
                         (fn [e] (-> e
                                     (assoc :updated-at now)
                                     (update :compaction-count inc))))
    compaction-entry))

(defn truncate-after-compaction! [state-dir identifier]
  (let [entry         (get-session state-dir identifier)
        transcript    (read-transcript-raw state-dir (:session-file entry))
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
          (write-transcript! state-dir (:session-file entry) new-transcript)
          (count removed-ids))))))

(defn update-tokens! [state-dir identifier {:keys [cache-read cache-write] :as updates}]
  (let [updates        (normalize-session-entry-keys updates)
        input-tokens  (:input-tokens updates)
        output-tokens (:output-tokens updates)]
   (update-index-entry! state-dir identifier
                         (fn [entry]
                           (cond-> (-> entry
                                       (update :input-tokens + (or input-tokens 0))
                                       (assoc :last-input-tokens (or input-tokens 0))
                                       (update :output-tokens + (or output-tokens 0))
                                       (assoc :total-tokens (+ (+ (:input-tokens entry) (or input-tokens 0))
                                                              (+ (:output-tokens entry) (or output-tokens 0)))))
                             cache-read  (update :cache-read (fnil + 0) cache-read)
                             cache-write (update :cache-write (fnil + 0) cache-write))))))

;; endregion ^^^^^ Public API ^^^^^
