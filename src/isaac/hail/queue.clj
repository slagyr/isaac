(ns isaac.hail.queue
  (:require
    [clojure.edn :as edn]
    [clojure.pprint :as pprint]
    [isaac.fs :as fs]
    [isaac.naming :as naming]
    [isaac.nexus :as nexus]
    [isaac.tool.memory :as memory]))

(defn- write-edn [value]
  (binding [*print-namespace-maps* false]
    (with-out-str (pprint/pprint value))))

(defn- runtime-state-dir []
  (or (nexus/state-dir) (throw (ex-info "hail queue requires :state-dir" {}))))

(defn- filesystem []
  (or (fs/instance) (throw (ex-info "hail.queue requires :fs in system" {}))))

(defn- pending-dir []
  (str (runtime-state-dir) "/hail/pending"))

(defn- pending-path [id]
  (str (pending-dir) "/" id ".edn"))

(defn- naming-strategy [state-dir fs*]
  (naming/->SequentialStrategy state-dir "hail" "hail-" fs*))

(defn- next-id [state-dir fs*]
  (naming/generate (naming-strategy state-dir fs*)))

(defn- normalize-record [record]
  (cond-> record
    (nil? (:id record))      (assoc :id (next-id (runtime-state-dir) (filesystem)))
    (nil? (:sent-at record)) (assoc :sent-at (str (memory/now)))))

(defn- read-record [path]
  (let [fs* (filesystem)]
    (when (fs/exists? fs* path)
      (let [record (edn/read-string (fs/slurp fs* path))]
        (if (map? record)
          (into {} (map (fn [[k v]] [(if (keyword? k) k (keyword k)) v]) record))
          record)))))

(defn send! [record]
  (let [fs*    (filesystem)
        record (normalize-record record)
        path   (pending-path (:id record))]
    (fs/mkdirs fs* (fs/parent path))
    (fs/spit fs* path (write-edn record))
    record))

(defn read-pending [id]
  (read-record (pending-path id)))
