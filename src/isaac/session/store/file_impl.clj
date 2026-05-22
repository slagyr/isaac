;; mutation-tested: 2026-05-06
(ns isaac.session.store.file-impl
  (:require
    [clojure.edn :as edn]
    [isaac.fs :as fs]
    [isaac.logger :as log]
    [isaac.session.naming :as naming]
    [isaac.session.store.impl-common :as c]
    [isaac.system :as system])
  (:import
    (java.time Instant ZoneOffset)
    (java.time.format DateTimeFormatter)))

;; region ----- Impl-specific -----

(def ^:private ts-formatter
  (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss"))

(defn- now-iso []
  (.format ts-formatter (.atOffset (Instant/now) ZoneOffset/UTC)))

(defn- ms->iso [ms]
  (.format ts-formatter (.atOffset (Instant/ofEpochMilli ms) ZoneOffset/UTC)))

(defn- normalize-timestamp [ts] (c/normalize-timestamp ms->iso ts))

(defn- runtime-fs! []
  (or (:fs (system/current))
      (throw (ex-info "file session store requires explicit fs or installed runtime :fs" {}))))

;; endregion ^^^^^ Impl-specific ^^^^^

;; region ----- Local wrappers -----

(defn session-id [identifier] (c/session-id identifier))

(defn- with-session-defaults [entry]
  (c/with-session-defaults now-iso normalize-timestamp entry))

(defn- migrate-transcript! [state-dir session-file fs]
  (c/migrate-transcript! normalize-timestamp state-dir session-file fs))

(defn- normalize-index-store [raw]
  (c/normalize-index-store with-session-defaults raw))

(defn- read-sidecar-entry [state-dir file-name fs]
  (c/read-sidecar-entry with-session-defaults state-dir file-name fs))

(defn- read-sidecar-store [state-dir fs]
  (c/read-sidecar-store with-session-defaults state-dir fs))

;; endregion ^^^^^ Local wrappers ^^^^^

;; region ----- Storage -----

(defn- write-sidecar! [state-dir {:keys [id] :as entry} fs]
  (let [path (c/sidecar-path state-dir id)]
    (c/mkdirs*! fs (fs/parent path))
    (c/spit*! fs path (c/write-edn entry))))

(defn- read-legacy-index-store [state-dir fs]
  (let [path (c/index-path state-dir)
        raw  (if (c/exists?* fs path) (edn/read-string (c/slurp* fs path)) {})]
    (normalize-index-store raw)))

(defn- migrate-legacy-index! [state-dir fs]
  (let [legacy-store  (read-legacy-index-store state-dir fs)
        sidecar-store (read-sidecar-store state-dir fs)]
    (doseq [[id entry] legacy-store
            :when (not (contains? sidecar-store id))]
      (when (and (:session-file entry)
                 (c/exists?* fs (c/transcript-path state-dir (:session-file entry))))
        (migrate-transcript! state-dir (:session-file entry) fs))
      (write-sidecar! state-dir entry fs))))

(defn- read-session-store [state-dir fs]
  (migrate-legacy-index! state-dir fs)
  (let [store (read-sidecar-store state-dir fs)]
    (doseq [entry (vals store)
            :when (and (:session-file entry)
                       (c/exists?* fs (c/transcript-path state-dir (:session-file entry))))]
      (migrate-transcript! state-dir (:session-file entry) fs))
    store))

(defn- update-sidecar-entry! [state-dir identifier updater fs]
  (let [store (read-session-store state-dir fs)]
    (when-let [id (c/resolve-entry-id store identifier)]
      (let [entry (c/conform-session! (updater (get store id)))]
        (write-sidecar! state-dir entry fs)
        entry))))

;; endregion ^^^^^ Storage ^^^^^

;; region ----- Public API -----

(defn create-session!
  ([state-dir identifier]
   (create-session! state-dir identifier {} (runtime-fs!)))
  ([state-dir identifier opts]
   (create-session! state-dir identifier opts (runtime-fs!)))
  ([state-dir identifier opts fs]
   (let [opts               (c/entry-defaults opts)
         store              (read-session-store state-dir fs)
         name               (or identifier (naming/generate (naming/strategy state-dir fs) {:state-dir state-dir :store store :fs fs}))
         id                 (c/session-id name)
         existing           (get store id)
         transcript-exists? (when (and existing (:session-file existing))
                              (c/exists?* fs (c/transcript-path state-dir (:session-file existing))))]
     (cond
       (and existing transcript-exists? (not= name (:name existing)))
       (throw (ex-info (str "session already exists: " id)
                       {:name name :session-id id}))

       (and existing transcript-exists?)
       (do
         (log/info :session/opened :sessionId id)
         existing)

       :else
       (let [session-file  (str id ".jsonl")
             now           (or (normalize-timestamp (:updated-at opts)) (now-iso))
             retention     (c/resolve-history-retention state-dir opts fs)
             transcript-id (c/new-id)
             header        {:type      "session"
                            :id        transcript-id
                            :timestamp now
                            :version   3
                            :cwd       (System/getProperty "user.dir")}
             entry         (with-session-defaults
                             {:id                id
                              :key               id
                              :name              name
                              :sessionId         transcript-id
                              :session-file      session-file
                              :origin            (:origin opts)
                              :history-retention retention
                              :created-at        now
                              :updated-at        now
                              :cwd               (or (:cwd opts) (System/getProperty "user.dir"))
                              :crew              (:crew opts)
                              :channel           (:channel opts)
                              :chat-type         (or (:chat-type opts) (:chatType opts))
                              :compaction-count  0
                              :input-tokens      0
                              :last-input-tokens 0
                              :output-tokens     0
                              :total-tokens      0})]
         (c/write-transcript! state-dir session-file [header] fs)
         (write-sidecar! state-dir (c/conform-session! entry) fs)
         (log/info :session/created :sessionId id)
         entry)))))

(def ^:private api
  (c/make-api {:runtime-fs-fn   runtime-fs!
               :read-store-fn   read-session-store
               :migrate-fn      migrate-transcript!
               :update-entry-fn update-sidecar-entry!
               :normalize-ts-fn normalize-timestamp
               :now-iso-fn      now-iso}))

(def get-session                (:get-session api))
(def list-sessions              (:list-sessions api))
(def most-recent-session        (:most-recent-session api))
(def get-transcript             (:get-transcript api))
(def active-transcript          (:active-transcript api))
(def update-session!            (:update-session! api))
(def append-message!            (:append-message! api))
(def append-error!              (:append-error! api))
(def append-compaction!         (:append-compaction! api))
(def splice-compaction!         (:splice-compaction! api))
(def truncate-after-compaction! (:truncate-after-compaction! api))

(defn open-session
  ([state-dir identifier]
   (open-session state-dir identifier (runtime-fs!)))
  ([state-dir identifier fs]
   (when-let [entry (get-session state-dir identifier fs)]
     (log/info :session/opened :sessionId (:id entry))
     entry)))

(defn delete-session!
  ([state-dir identifier]
   (delete-session! state-dir identifier (runtime-fs!)))
  ([state-dir identifier fs]
   (let [store (read-session-store state-dir fs)]
     (when-let [id (c/resolve-entry-id store identifier)]
       (let [entry (get store id)
             path  (c/transcript-path state-dir (:session-file entry))
             meta  (c/sidecar-path state-dir id)]
         (when (c/exists?* fs meta) (c/delete*! fs meta))
         (when (c/exists?* fs path) (c/delete*! fs path))
         true)))))

(defn update-tokens!
  ([state-dir identifier updates]
   (update-tokens! state-dir identifier updates (runtime-fs!)))
  ([state-dir identifier {:keys [cache-read cache-write] :as updates} fs]
   (let [input-tokens  (:input-tokens updates)
         output-tokens (:output-tokens updates)]
     (update-sidecar-entry! state-dir identifier
                            (fn [entry]
                              (cond-> (-> entry
                                          (update :input-tokens + (or input-tokens 0))
                                          (assoc :last-input-tokens (or input-tokens 0))
                                          (update :output-tokens + (or output-tokens 0))
                                          (assoc :total-tokens (+ (+ (:input-tokens entry) (or input-tokens 0))
                                                                  (+ (:output-tokens entry) (or output-tokens 0)))))
                                cache-read  (update :cache-read (fnil + 0) cache-read)
                                cache-write (update :cache-write (fnil + 0) cache-write)))
                            fs))))

;; endregion ^^^^^ Public API ^^^^^
