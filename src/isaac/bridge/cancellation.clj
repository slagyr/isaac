(ns isaac.bridge.cancellation)

;; region ----- State -----

(defonce ^:private active-turns (atom {}))
(defonce ^:private pending-cancels (atom #{}))

;; endregion ^^^^^ State ^^^^^

;; region ----- Public API -----

(defn cancelled-result []
  {:stopReason "cancelled"})

(defn cancelled-response? [result]
  (= "cancelled" (:stopReason result)))

(defn begin-turn! [session-key]
  (let [turn {:cancelled? (atom false)
              :hooks      (atom [])}]
    (swap! active-turns assoc session-key turn)
    (when (contains? @pending-cancels session-key)
      (swap! pending-cancels disj session-key)
      (reset! (:cancelled? turn) true))
    (when (contains? @pending-cancels session-key)
      (swap! pending-cancels disj session-key)
      (reset! (:cancelled? turn) true))
    turn))

(defn end-turn! [session-key turn]
  (swap! active-turns #(if (identical? turn (get % session-key))
                         (dissoc % session-key)
                         %))
  nil)

(defn cancelled? [session-key]
  (or (contains? @pending-cancels session-key)
      (some-> (get @active-turns session-key) :cancelled? deref boolean)))

(defn on-cancel! [session-key f]
  (when (and session-key f)
    (if-let [turn (get @active-turns session-key)]
      (do
        (swap! (:hooks turn) conj f)
        (when @(:cancelled? turn)
          (f)))
      (when (contains? @pending-cancels session-key)
        (f))))
  nil)

(defn cancel! [session-key]
  (when session-key
    (if-let [turn (get @active-turns session-key)]
      (do
        (reset! (:cancelled? turn) true)
        (doseq [hook @(:hooks turn)]
          (try
            (hook)
            (catch Exception _ nil)))
        true)
      (do
        (swap! pending-cancels conj session-key)
        true))))

;; endregion ^^^^^ Public API ^^^^^
