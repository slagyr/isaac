(ns isaac.session.store.memory
  (:require
    [isaac.session.store :as store]))

(defn- get-val [m k]
  (or (get m k) (get m (name k))))

(deftype MemorySessionStore [state]
  store/SessionStore
  (open-session! [_ name opts]
    (let [entry (merge {:key name} opts)]
      (swap! state update name #(or % entry))
      (get @state name)))
  (delete-session! [_ name]
    (let [exists? (contains? @state name)]
      (swap! state dissoc name)
      exists?))
  (list-sessions [_]
    (vals @state))
  (list-sessions-by-agent [_ agent]
    (filter #(= agent (:crew %)) (vals @state)))
  (most-recent-session [_]
    (last (vals @state)))
  (get-session [_ name]
    (get @state name))
  (get-transcript [_ _]
    [])
  (update-session! [_ name updates]
    (swap! state update name merge updates)
    (get @state name))
  (append-message! [_ name message]
    (swap! state update name
           (fn [session]
             (let [channel (get-val message :channel)
                   to      (get-val message :to)]
               (cond-> session
                 channel (assoc :last-channel channel)
                 to      (assoc :last-to to)))))
    (get @state name))
  (append-error! [_ _ error]
    error)
  (append-compaction! [_ _ compaction]
    compaction)
  (splice-compaction! [_ _ compaction]
    compaction)
  (truncate-after-compaction! [_ _]
    nil))

(defn create-store []
  (->MemorySessionStore (atom {})))

(defn state [^MemorySessionStore store]
  @(.-state store))
