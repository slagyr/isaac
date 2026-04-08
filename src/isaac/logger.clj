(ns isaac.logger)

;; region ----- Configuration -----

(def ^:private levels {:error 0 :warn 1 :report 2 :info 3 :debug 4})

(defonce ^:private state
  (atom {:level    :debug
         :log-file "/tmp/isaac.log"}))

(defn set-level! [level]
  (swap! state assoc :level level))

(defn set-log-file! [path]
  (swap! state assoc :log-file path))

;; endregion ^^^^^ Configuration ^^^^^

;; region ----- Core -----

(defn enabled? [level]
  (<= (get levels level 4) (get levels (:level @state) 4)))

(defn log* [level data file line]
  (when (enabled? level)
    (let [entry (merge {:ts    (System/currentTimeMillis)
                        :level level
                        :file  file
                        :line  line}
                       data)]
      (spit (:log-file @state) (str (pr-str entry) "\n") :append true))))

;; endregion ^^^^^ Core ^^^^^

;; region ----- Macros -----

(defmacro log [level data]
  `(log* ~level ~data ~*file* ~(:line (meta &form))))

(defmacro error  [data] `(log* :error  ~data ~*file* ~(:line (meta &form))))
(defmacro warn   [data] `(log* :warn   ~data ~*file* ~(:line (meta &form))))
(defmacro report [data] `(log* :report ~data ~*file* ~(:line (meta &form))))
(defmacro info   [data] `(log* :info   ~data ~*file* ~(:line (meta &form))))
(defmacro debug  [data] `(log* :debug  ~data ~*file* ~(:line (meta &form))))

;; endregion ^^^^^ Macros ^^^^^
