(ns isaac.session.store)

(defprotocol SessionStore
  (open-session! [this name opts])
  (delete-session! [this name])
  (list-sessions [this])
  (list-sessions-by-agent [this agent])
  (most-recent-session [this])
  (get-session [this name])
  (get-transcript [this name])
  (update-session! [this name updates])
  (append-message! [this name message])
  (append-error! [this name error])
  (append-compaction! [this name compaction])
  (splice-compaction! [this name compaction])
  (truncate-after-compaction! [this name]))

(defn create
  "Create a SessionStore for the given state directory and impl keyword.
   :memory            — in-memory store (ephemeral, fast)
   :jsonl-edn-sidecar — file store with per-session EDN sidecar files (default)
   :jsonl-edn-index   — file store with single combined index (not yet implemented)"
  ([state-dir] (create state-dir :jsonl-edn-sidecar))
  ([state-dir impl]
   (case impl
     :memory            ((requiring-resolve 'isaac.session.store.memory/create-store))
     :jsonl-edn-index   ((requiring-resolve 'isaac.session.store.index/create-store) state-dir)
     ((requiring-resolve 'isaac.session.store.file/create-store) state-dir))))

(defn register!
  "Create a store from config and register it in the system under :session-store.
   Reads :session-store :impl from cfg (defaults to :jsonl-edn-sidecar) and state-dir from system."
  [cfg state-dir]
  (let [impl  (get-in cfg [:session-store :impl] :jsonl-edn-sidecar)
        store (create state-dir impl)]
    ((requiring-resolve 'isaac.system/register!) :session-store store)
    store))
