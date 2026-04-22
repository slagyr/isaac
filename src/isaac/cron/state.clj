(ns isaac.cron.state
  (:require
    [clojure.edn :as edn]
    [clojure.pprint :as pprint]
    [isaac.fs :as fs]))

(defn- cron-state-path [state-dir]
  (str state-dir "/cron.edn"))

(defn- write-edn [value]
  (binding [*print-namespace-maps* false]
    (with-out-str (pprint/pprint value))))

(defn read-state [state-dir]
  (let [path (cron-state-path state-dir)]
    (if (fs/exists? path)
      (or (edn/read-string (fs/slurp path)) {})
      {})))

(defn write-job-state! [state-dir job-name attrs]
  (let [path    (cron-state-path state-dir)
        current (read-state state-dir)
        updated (update current (str job-name) #(merge (or % {}) attrs))]
    (fs/mkdirs (fs/parent path))
    (fs/spit path (write-edn updated))
    updated))
