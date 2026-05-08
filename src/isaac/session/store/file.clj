(ns isaac.session.store.file
  (:require
    [isaac.session.storage :as storage]
    [isaac.session.store :as store]))

(deftype FileSessionStore [state-dir naming-strategy-key]
  store/SessionStore
  (open-session! [_ name opts]
    (storage/create-session! state-dir name opts))
  (delete-session! [_ name]
    (storage/delete-session! state-dir name))
  (list-sessions [_]
    (storage/list-sessions state-dir))
  (list-sessions-by-agent [_ agent]
    (storage/list-sessions state-dir agent))
  (most-recent-session [_]
    (storage/most-recent-session state-dir))
  (get-session [_ name]
    (storage/get-session state-dir name))
  (get-transcript [_ name]
    (storage/get-transcript state-dir name))
  (update-session! [_ name updates]
    (storage/update-session! state-dir name updates))
  (append-message! [_ name message]
    (storage/append-message! state-dir name message))
  (append-error! [_ name error]
    (storage/append-error! state-dir name error))
  (append-compaction! [_ name compaction]
    (storage/append-compaction! state-dir name compaction))
  (splice-compaction! [_ name compaction]
    (storage/splice-compaction! state-dir name compaction))
  (truncate-after-compaction! [_ name]
    (storage/truncate-after-compaction! state-dir name)))

(defn create-store
  ([state-dir]
   (create-store state-dir nil))
  ([state-dir naming-strategy-key]
   (->FileSessionStore state-dir naming-strategy-key)))
