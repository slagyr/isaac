(ns isaac.session.store.file
  (:require
    [isaac.fs :as fs]
    [isaac.session.store.file-impl :as storage]
    [isaac.session.store :as store]
    [isaac.system :as system]))

(defn- runtime-fs []
  (or (:fs (system/current))
      fs/*fs*))

(defn- with-store-fs [fs* f]
  (binding [fs/*fs* fs*]
    (f)))

(deftype FileSessionStore [state-dir naming-strategy-key fs]
  store/SessionStore
  (open-session! [_ name opts]
    (with-store-fs fs #(storage/create-session! state-dir name opts)))
  (delete-session! [_ name]
    (with-store-fs fs #(storage/delete-session! state-dir name)))
  (list-sessions [_]
    (with-store-fs fs #(storage/list-sessions state-dir)))
  (list-sessions-by-agent [_ agent]
    (with-store-fs fs #(storage/list-sessions state-dir agent)))
  (most-recent-session [_]
    (with-store-fs fs #(storage/most-recent-session state-dir)))
  (get-session [_ name]
    (with-store-fs fs #(storage/get-session state-dir name)))
  (get-transcript [_ name]
    (with-store-fs fs #(storage/get-transcript state-dir name)))
  (active-transcript [_ name]
    (with-store-fs fs #(storage/active-transcript state-dir name)))
  (update-session! [_ name updates]
    (with-store-fs fs #(storage/update-session! state-dir name updates)))
  (append-message! [_ name message]
    (with-store-fs fs #(storage/append-message! state-dir name message)))
  (append-error! [_ name error]
    (with-store-fs fs #(storage/append-error! state-dir name error)))
  (append-compaction! [_ name compaction]
    (with-store-fs fs #(storage/append-compaction! state-dir name compaction)))
  (splice-compaction! [_ name compaction]
    (with-store-fs fs #(storage/splice-compaction! state-dir name compaction)))
  (truncate-after-compaction! [_ name]
    (with-store-fs fs #(storage/truncate-after-compaction! state-dir name))))

(defn create-store
  ([state-dir]
   (create-store state-dir nil))
  ([state-dir naming-strategy-key]
   (create-store state-dir naming-strategy-key (runtime-fs)))
  ([state-dir naming-strategy-key fs*]
   (->FileSessionStore state-dir naming-strategy-key fs*)))
