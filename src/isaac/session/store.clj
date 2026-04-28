(ns isaac.session.store)

(defn create-store []
  (atom {}))

(defn create-session! [store key-str]
  (swap! store assoc key-str {:key key-str}))

(defn list-sessions [store]
  (vals @store))

(defn get-session [store key-str]
  (get @store key-str))

(defn- get-val [m k]
  (or (get m k) (get m (name k))))

(defn append-message! [store key-str message]
  (swap! store update key-str
         (fn [session]
           (let [channel (get-val message :channel)
                 to      (get-val message :to)]
             (cond-> session
               channel (assoc :last-channel channel)
               to      (assoc :last-to to))))))
