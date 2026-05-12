(ns isaac.log-viewer
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]))

;; region ----- ANSI helpers -----

(defn- ansi [& codes]
  (str "\033[" (str/join ";" codes) "m"))

(def ^:private reset      (ansi 0))
(def ^:private dim        (ansi 2))
(def ^:private bg-zebra   (ansi "48;5;236"))

(def ^:private palette
  [(ansi 36) (ansi 33) (ansi 35) (ansi 32) (ansi 34) (ansi 91)])

(defn color-for-ns [s]
  (nth palette (mod (Math/abs (hash (str s))) (count palette))))

(defn color-for-session [s]
  (nth palette (mod (Math/abs (hash (str s))) (count palette))))

(defn color-for-level [level]
  (case level
    :error (ansi 1 31)
    :warn  (ansi 1 33)
    :info  (ansi 1 36)
    :debug dim
    :trace dim
    (ansi 0)))

(defn color-for-value [v]
  (cond
    (nil? v)     (ansi 31)
    (boolean? v) (ansi 33)
    (number? v)  (ansi 32)
    (keyword? v) (ansi 35)
    :else        ""))

;; endregion ^^^^^ ANSI helpers ^^^^^

;; region ----- Formatting -----

(defn format-time [ts]
  (try
    (let [inst (java.time.Instant/parse (str ts))
          ldt  (-> inst
                   (.atZone (java.time.ZoneId/systemDefault))
                   .toLocalDateTime)
          fmt  (java.time.format.DateTimeFormatter/ofPattern "HH:mm:ss.SSS")]
      (.format fmt ldt))
    (catch Exception _
      (let [s (str ts)]
        (if (>= (count s) 12) (subs s 0 12) s)))))

(defn- format-kv [k v color?]
  (let [k-str (name k)
        v-str (pr-str v)]
    (if color?
      (let [val-color (if (and (= k :sessionId) (string? v))
                        (color-for-session v)
                        (color-for-value v))]
        (str dim k-str "=" reset val-color v-str reset))
      (str k-str "=" v-str))))

(defn format-entry [entry color?]
  (let [ts         (get entry :ts "")
        level      (get entry :level :info)
        event      (get entry :event "")
        kvs        (dissoc entry :ts :level :event :file :line)
        time-str   (format-time ts)
        level-str  (format "%-5s" (str/upper-case (name (or level "INFO"))))
        event-str  (str event)
        event-ns   (when (keyword? event) (namespace event))
        time-part  (if color? (str dim time-str reset "  ") (str time-str "  "))
        level-part (if color?
                     (str (color-for-level level) level-str reset "  ")
                     (str level-str "  "))
        event-part (if (and color? event-ns)
                     (str (color-for-ns event-ns) event-str reset)
                     event-str)
        kv-part    (when (seq kvs)
                     (str "  " (str/join "  " (map (fn [[k v]] (format-kv k v color?)) kvs))))]
    (str time-part level-part event-part kv-part)))

(defn format-line [line color?]
  (let [line (str/trim (or line ""))]
    (when-not (str/blank? line)
      (try
        (let [entry (edn/read-string line)]
          (if (map? entry)
            (format-entry entry color?)
            line))
        (catch Exception _
          line)))))

;; endregion ^^^^^ Formatting ^^^^^

;; region ----- Tailing -----

(defn tty? []
  (some? (System/console)))

(defn tail!
  "Tail a log file, printing formatted lines. Blocks in follow mode.
   opts: :color? (bool), :follow? (bool, default true), :zebra? (bool, default false)"
  [path {:keys [color? follow? zebra?] :or {follow? true color? false zebra? false}}]
  (let [file (java.io.File. path)]
    (with-open [raf (java.io.RandomAccessFile. file "r")]
      (.seek raf 0)
      (loop [row 0]
        (if-let [line (.readLine raf)]
          (let [formatted (format-line line color?)]
            (when formatted
              (println (if (and zebra? color? (odd? row))
                         (str bg-zebra formatted reset)
                         formatted)))
            (recur (if formatted (inc row) row)))
          (when follow?
            (Thread/sleep 100)
            (recur row)))))))

;; endregion ^^^^^ Tailing ^^^^^
