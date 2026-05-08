(ns isaac.cron.state
  (:require
    [clojure.edn :as edn]
    [clojure.pprint :as pprint]
    [isaac.fs :as fs]
    [isaac.system :as system]))

(defn- cron-state-path []
  (str (system/get :state-dir) "/cron.edn"))

(defn- write-edn [value]
  (binding [*print-namespace-maps* false]
    (with-out-str (pprint/pprint value))))

(defn read-state []
  (let [path (cron-state-path)]
    (if (fs/exists? path)
      (or (edn/read-string (fs/slurp path)) {})
      {})))

(defn write-job-state! [job-name attrs]
  (let [path    (cron-state-path)
        current (read-state)
        updated (update current (str job-name) #(merge (or % {}) attrs))]
    (fs/mkdirs (fs/parent path))
    (fs/spit path (write-edn updated))
    updated))
