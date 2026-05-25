(ns isaac.hail.queue
  (:require
    [clojure.edn :as edn]
    [clojure.pprint :as pprint]
    [isaac.config.api :as config]
    [isaac.fs :as fs]
    [isaac.naming :as naming]
    [isaac.tool.memory :as memory]))

(defn- write-edn [value]
  (binding [*print-namespace-maps* false]
    (with-out-str (pprint/pprint value))))

(defn- runtime-state-dir []
  (or (config/state-dir) (throw (ex-info "hail queue requires :state-dir" {}))))

(defn- filesystem []
  (or (fs/instance) (throw (ex-info "hail.queue requires :fs in system" {}))))

(defn- pending-dir []
  (str (runtime-state-dir) "/hail/pending"))

(defn- pending-path [id]
  (str (pending-dir) "/" id ".edn"))

(defn- temp-path [id]
  (str (pending-dir) "/" id ".tmp"))

(defn- naming-strategy [state-dir fs*]
  (naming/->SequentialStrategy state-dir "hail" "hail-" fs*))

(defn- next-id [state-dir fs*]
  (naming/generate (naming-strategy state-dir fs*)))

(defn- normalize-record [record state-dir fs*]
  (-> record
      (assoc :id (next-id state-dir fs*))
      (assoc :sent-at (str (memory/now)))))

(defn- read-record [path]
  (let [fs* (filesystem)]
    (when (fs/exists? fs* path)
      (let [record (edn/read-string (fs/slurp fs* path))]
        (if (map? record)
          (into {} (map (fn [[k v]] [(if (keyword? k) k (keyword k)) v]) record))
          record)))))

(defn send! [record]
  (let [fs*    (filesystem)
        state-dir (runtime-state-dir)
        record (normalize-record record state-dir fs*)
        path   (pending-path (:id record))
        temp   (temp-path (:id record))]
    (fs/mkdirs fs* (fs/parent path))
    (fs/spit fs* temp (write-edn record))
    (fs/move fs* temp path)
    record))

(defn read-pending [id]
  (read-record (pending-path id)))
