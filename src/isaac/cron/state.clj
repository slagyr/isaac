(ns isaac.cron.state
  (:require
    [clojure.edn :as edn]
    [clojure.pprint :as pprint]
    [isaac.config.api :as config]
    [isaac.fs :as fs]))

(defn- cron-state-path [state-dir]
  (str state-dir "/cron.edn"))

(defn- write-edn [value]
  (binding [*print-namespace-maps* false]
    (with-out-str (pprint/pprint value))))

(defn- runtime-state-dir []
  (or (config/state-dir) (throw (ex-info "cron state requires :state-dir" {}))))

(defn- runtime-fs! []
  (or (fs/instance) (throw (ex-info "cron state requires :fs in system" {}))))

(defn read-state
  ([]
   (read-state (runtime-state-dir)))
  ([state-dir]
   (let [fs*  (runtime-fs!)
          path (cron-state-path state-dir)]
     (if (fs/exists? fs* path)
       (or (edn/read-string (fs/slurp fs* path)) {})
        {}))))

(defn write-job-state!
  ([job-name attrs]
   (write-job-state! (runtime-state-dir) job-name attrs))
  ([state-dir job-name attrs]
   (let [fs*     (runtime-fs!)
          path    (cron-state-path state-dir)
          current (read-state state-dir)
         updated (update current (str job-name) #(merge (or % {}) attrs))]
     (fs/mkdirs fs* (fs/parent path))
     (fs/spit fs* path (write-edn updated))
     updated)))
