(ns isaac.bridge.cancellation)

;; region ----- State -----

(def ^:private turns* (atom {}))

(defn- new-turn []
  {:cancelled? (atom false)
   :hooks      (atom [])})

;; endregion ^^^^^ State ^^^^^

;; region ----- Public API -----

(defn cancelled-result []
  {:stopReason "cancelled"})

(defn cancelled-response? [result]
  (= "cancelled" (:stopReason result)))

(defn clear! []
  (reset! turns* {})
  nil)

(defn begin-turn! [session-key]
  (let [turn (or (get @turns* session-key)
                 (new-turn))]
    (swap! turns* assoc session-key turn)
    turn))

(defn end-turn! [session-key turn]
  (swap! turns* #(if (identical? turn (get % session-key))
                   (dissoc % session-key)
                   %))
  nil)

(defn cancelled? [session-key]
  (some-> (get @turns* session-key) :cancelled? deref boolean))

(defn on-cancel! [session-key f]
  (when (and session-key f)
    (if-let [turn (get @turns* session-key)]
      (do
        (swap! (:hooks turn) conj f)
        (when @(:cancelled? turn)
          (f)))
      nil))
  nil)

(defn cancel! [session-key]
  (when session-key
    (let [turn (or (get @turns* session-key)
                   (let [turn (new-turn)]
                     (get (swap! turns* assoc session-key turn) session-key)))]
      (reset! (:cancelled? turn) true)
      (doseq [hook @(:hooks turn)]
        (try
          (hook)
          (catch Exception _ nil)))
      true)))

;; endregion ^^^^^ Public API ^^^^^
