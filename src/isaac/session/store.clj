(ns isaac.session.store
  (:require
    [isaac.system :as system]))

(defprotocol SessionStore
  (open-session! [this name opts])
  (delete-session! [this name])
  (list-sessions [this])
  (list-sessions-by-agent [this agent])
  (most-recent-session [this])
  (get-session [this name])
  (get-transcript [this name])
  (active-transcript [this name])
  (update-session! [this name updates])
  (append-message! [this name message])
  (append-error! [this name error])
  (append-compaction! [this name compaction])
  (splice-compaction! [this name compaction])
  (truncate-after-compaction! [this name]))

;; ----- Impl factory registry -----
;; Each impl namespace (memory/file/index) implements SessionStore (so requires
;; this ns) and registers its create-store fn at load time. We can't require
;; them from here without forming a cycle.
(defonce ^:private factories* (atom {}))

(defn register-factory!
  "Each session store impl namespace calls this at load time."
  [impl-kw factory]
  (swap! factories* assoc impl-kw factory))

(def ^:private default-impl :jsonl-edn-sidecar)
(def ^:private impl->ns
  {:memory          'isaac.session.store.memory
   :jsonl-edn-index 'isaac.session.store.index
   default-impl     'isaac.session.store.file})

(defn create
  "Create a SessionStore for the given state directory and impl keyword.
   :memory            — in-memory store (ephemeral, fast)
   :jsonl-edn-sidecar — file store with per-session EDN sidecar files (default)
   :jsonl-edn-index   — file store with single combined index"
  ([state-dir] (create state-dir default-impl))
  ([state-dir impl]
   (when-not (contains? @factories* impl)
     ;; Lazy-load the impl ns if no caller has loaded it yet. The impl's
     ;; load triggers a self-registration into factories*.
     (when-let [ns-sym (get impl->ns impl)]
       (require ns-sym)))
    (let [factory (or (get @factories* impl)
                      (throw (ex-info (str "no session store factory for impl " impl)
                                      {:impl impl :registered (vec (sort (keys @factories*)))})))]
      (factory state-dir))))

(defn resolve-store
  "Resolve a session store from an explicit :session-store or create one from
   :state-dir. caller is used only to make missing-context errors specific."
  [ctx caller]
  (or (:session-store ctx)
      (some-> (:state-dir ctx) create)
      (throw (ex-info (str caller " requires :state-dir or :session-store")
                      {:ctx-keys (-> ctx keys sort vec)}))))

(defn register!
  "Create a store from config and register it in the system under :session-store.
   Reads :session-store :impl from cfg (defaults to :jsonl-edn-sidecar) and state-dir from system."
  [cfg state-dir]
  (let [impl  (get-in cfg [:session-store :impl] :jsonl-edn-sidecar)
        store (create state-dir impl)]
    (system/register! :session-store store)
    store))
