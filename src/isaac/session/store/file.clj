(ns isaac.session.store.file
  (:require
    [isaac.fs :as fs]
    [isaac.session.store.file-impl :as storage]
    [isaac.session.store :as store]
    [isaac.system :as system]))

(defn- runtime-fs []
  (or (:fs (system/current))
      (throw (ex-info "session.store.file requires :fs in system"
                      {:current-keys (-> (system/current) keys sort vec)}))))

(deftype FileSessionStore [state-dir naming-strategy-key fs]
  store/SessionStore
  (open-session! [_ name opts]
    (storage/create-session! state-dir name opts fs))
  (delete-session! [_ name]
    (storage/delete-session! state-dir name fs))
  (list-sessions [_]
    (storage/list-sessions state-dir nil fs))
  (list-sessions-by-agent [_ agent]
    (storage/list-sessions state-dir agent fs))
  (most-recent-session [_]
    (storage/most-recent-session state-dir nil fs))
  (get-session [_ name]
    (storage/get-session state-dir name fs))
  (get-transcript [_ name]
    (storage/get-transcript state-dir name fs))
  (active-transcript [_ name]
    (storage/active-transcript state-dir name fs))
  (update-session! [_ name updates]
    (storage/update-session! state-dir name updates fs))
  (append-message! [_ name message]
    (storage/append-message! state-dir name message fs))
  (append-error! [_ name error]
    (storage/append-error! state-dir name error fs))
  (append-compaction! [_ name compaction]
    (storage/append-compaction! state-dir name compaction fs))
  (splice-compaction! [_ name compaction]
    (storage/splice-compaction! state-dir name compaction fs))
  (truncate-after-compaction! [_ name]
    (storage/truncate-after-compaction! state-dir name fs)))

(defn create-store
  ([state-dir]
   (create-store state-dir nil))
  ([state-dir naming-strategy-key]
   (create-store state-dir naming-strategy-key (runtime-fs)))
  ([state-dir naming-strategy-key fs*]
   (->FileSessionStore state-dir naming-strategy-key fs*)))

(store/register-factory! :jsonl-edn-sidecar #'create-store)
