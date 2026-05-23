(ns isaac.session.store.index
  (:require
    [clojure.edn :as edn]
    [isaac.fs :as fs]
    [isaac.session.store :as store]
    [isaac.session.store.impl-common :as c]
    [isaac.nexus :as nexus])
  (:import
    (java.time Instant ZoneOffset)
    (java.time.format DateTimeFormatter)))

;; region ----- Impl-specific -----

(def ^:private ts-formatter
  (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss.SSS"))

(defn- now-iso []
  (.format ts-formatter (.atOffset (Instant/now) ZoneOffset/UTC)))

(defn- ms->iso [ms]
  (.format ts-formatter (.atOffset (Instant/ofEpochMilli ms) ZoneOffset/UTC)))

(defn- normalize-timestamp [ts] (c/normalize-timestamp ms->iso ts))

(defn- runtime-fs! []
  (or (:fs (nexus/necho))
      (throw (ex-info "index session store requires explicit fs or installed runtime :fs" {}))))

;; endregion ^^^^^ Impl-specific ^^^^^

;; region ----- Helpers -----

(defn session-id [identifier] (c/session-id identifier))

(defn- with-session-defaults [entry]
  (c/with-session-defaults now-iso normalize-timestamp entry))

(defn- migrate-transcript! [state-dir session-file fs]
  (c/migrate-transcript! normalize-timestamp state-dir session-file fs))

(defn- normalize-index-store [raw]
  (c/normalize-index-store with-session-defaults raw))

(defn- read-sidecar-store [state-dir fs]
  (c/read-sidecar-store with-session-defaults state-dir fs))

;; endregion ^^^^^ Helpers ^^^^^

;; region ----- Storage -----

(defn- write-index! [state-dir store fs]
  (let [path (c/index-path state-dir)]
    (c/mkdirs*! fs (fs/parent path))
    (c/spit*! fs path (c/write-edn store))))

(defn- read-session-store [state-dir fs]
  (let [path  (c/index-path state-dir)
        store (if (c/exists?* fs path)
                (normalize-index-store (edn/read-string (c/slurp* fs path)))
                (let [sidecars (read-sidecar-store state-dir fs)]
                  (when (seq sidecars)
                    (write-index! state-dir sidecars fs))
                  sidecars))]
    (doseq [entry (vals store)
            :when (and (:session-file entry)
                       (c/exists?* fs (c/transcript-path state-dir (:session-file entry))))]
      (migrate-transcript! state-dir (:session-file entry) fs))
    store))

(defn- update-index-entry! [state-dir identifier updater fs]
  (let [store (read-session-store state-dir fs)]
    (when-let [id (c/resolve-entry-id store identifier)]
      (let [entry     (c/conform-session! (updater (get store id)))
            new-store (assoc store id entry)]
        (write-index! state-dir new-store fs)
        entry))))

;; endregion ^^^^^ Storage ^^^^^

;; region ----- Public API -----

(defn create-session!
  ([state-dir identifier]
   (create-session! state-dir identifier {} (runtime-fs!)))
  ([state-dir identifier opts]
   (create-session! state-dir identifier opts (runtime-fs!)))
  ([state-dir identifier opts fs]
   (c/create-session! read-session-store
                      (fn [store id entry] (write-index! state-dir (assoc store id entry) fs))
                      now-iso normalize-timestamp
                      state-dir identifier opts fs)))

(defn- get-session [state-dir identifier fs]
  (c/get-session read-session-store state-dir identifier fs))

(defn- get-transcript [state-dir identifier fs]
  (c/get-transcript get-session migrate-transcript! state-dir identifier fs))

(defn- delete-session! [state-dir identifier fs]
  (let [store (read-session-store state-dir fs)]
    (when-let [id (c/resolve-entry-id store identifier)]
      (let [entry     (get store id)
            path      (c/transcript-path state-dir (:session-file entry))
            new-store (dissoc store id)]
        (write-index! state-dir new-store fs)
        (when (c/exists?* fs path)
          (c/delete*! fs path))
        true))))

;; endregion ^^^^^ Public API ^^^^^

;; region ----- Store type -----

(deftype IndexSessionStore [state-dir fs]
  store/SessionStore
  (open-session! [_ name opts]
    (create-session! state-dir name opts fs))
  (delete-session! [_ name]
    (delete-session! state-dir name fs))
  (list-sessions [_]
    (c/list-sessions read-session-store state-dir nil fs))
  (list-sessions-by-agent [_ agent]
    (c/list-sessions read-session-store state-dir agent fs))
  (most-recent-session [_]
    (c/most-recent-session read-session-store state-dir nil fs))
  (get-session [_ name]
    (get-session state-dir name fs))
  (get-transcript [_ name]
    (get-transcript state-dir name fs))
  (active-transcript [_ name]
    (c/active-transcript get-session migrate-transcript! state-dir name fs))
  (update-session! [_ name updates]
    (c/update-session! update-index-entry! normalize-timestamp state-dir name updates fs))
  (append-message! [_ name message]
    (c/append-message! get-session get-transcript update-index-entry! now-iso state-dir name message fs))
  (append-error! [_ name error]
    (c/append-error! get-session get-transcript update-index-entry! now-iso state-dir name error fs))
  (append-compaction! [_ name compaction]
    (c/append-compaction! get-session get-transcript update-index-entry! now-iso state-dir name compaction fs))
  (splice-compaction! [_ name compaction]
    (c/splice-compaction! get-session get-transcript update-index-entry! now-iso state-dir name compaction fs))
  (truncate-after-compaction! [_ name]
    (c/truncate-after-compaction! get-session state-dir name fs)))

(defn create-store
  ([state-dir]
   (create-store state-dir (runtime-fs!)))
  ([state-dir fs*]
   (->IndexSessionStore state-dir fs*)))

(store/register-factory! :jsonl-edn-index #'create-store)

;; endregion ^^^^^ Store type ^^^^^
