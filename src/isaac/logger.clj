(ns isaac.logger
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]))

;; region ----- Configuration -----

(def ^:private levels {:error 0 :warn 1 :report 2 :info 3 :debug 4})

(defonce ^:private state
  (atom {:level    :debug
         :output   :file
         :log-file "/tmp/isaac.log"
         :entries  []}))

(defn set-level! [level]
  (swap! state assoc :level level))

(defn set-log-file! [path]
  (swap! state assoc :log-file path))

(defn set-output! [output]
  (swap! state assoc :output output))

(defn get-entries []
  (:entries @state))

(defn clear-entries! []
  (swap! state assoc :entries []))

;; endregion ^^^^^ Configuration ^^^^^

;; region ----- Core -----

(defn enabled? [level]
  (<= (get levels level 4) (get levels (:level @state) 4)))

(defn- iso-now []
  (str (java.time.Instant/now)))

(defn- normalize-file-path [file]
  (let [workspace   (System/getProperty "user.dir")
        normalized  (str/replace file "\\" "/")
        workspace*  (str/replace workspace "\\" "/")
        relative    (if (str/starts-with? normalized (str workspace* "/"))
                      (subs normalized (inc (count workspace*)))
                      normalized)]
    (or (when (re-matches #"(src|spec|features|test)/.*" relative)
          relative)
        (some (fn [dir]
                (let [candidate (str dir "/" relative)]
                  (when (.exists (io/file workspace candidate))
                    candidate)))
              ["src" "spec" "features" "test"])
        relative)))

(defn- build-entry [level data file line]
  (let [ts    (iso-now)
        base  (array-map :ts ts :level level :event (:event data))
        extra (dissoc data :event)]
    (-> base
        (into extra)
        (assoc :file (normalize-file-path file) :line line))))

(defn log* [level data file line]
  (when (enabled? level)
    (let [entry (build-entry level data file line)]
      (case (:output @state)
        :memory (swap! state update :entries conj entry)
        (spit (:log-file @state) (str (pr-str entry) "\n") :append true)))))

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
