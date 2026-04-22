(ns isaac.delivery.queue
  (:require
    [clojure.edn :as edn]
    [clojure.pprint :as pprint]
    [clojure.string :as str]
    [isaac.fs :as fs]
    [isaac.tool.memory :as memory])
  (:import
    (java.util UUID)))

(defn- write-edn [value]
  (binding [*print-namespace-maps* false]
    (with-out-str (pprint/pprint value))))

(defn- delivery-dir [state-dir]
  (str state-dir "/delivery"))

(defn- pending-dir [state-dir]
  (str (delivery-dir state-dir) "/pending"))

(defn- failed-dir [state-dir]
  (str (delivery-dir state-dir) "/failed"))

(defn- pending-path [state-dir id]
  (str (pending-dir state-dir) "/" id ".edn"))

(defn- failed-path [state-dir id]
  (str (failed-dir state-dir) "/" id ".edn"))

(defn- new-id []
  (-> (str (UUID/randomUUID))
      (str/replace "-" "")
      (subs 0 4)))

(defn- normalize-record [record]
  (-> record
      (update :id #(or % (new-id)))
      (update :attempts #(or % 0))
      (update :created-at #(or % (str (memory/now))))))

(defn- read-record [path]
  (when (fs/exists? path)
    (let [record (edn/read-string (fs/slurp path))]
      (if (map? record)
        (into {} (map (fn [[k v]] [(if (keyword? k) k (keyword k)) v]) record))
        record))))

(defn enqueue! [state-dir record]
  (let [record (normalize-record record)
        path   (pending-path state-dir (:id record))]
    (fs/mkdirs (fs/parent path))
    (fs/spit path (write-edn record))
    record))

(defn update-pending! [state-dir id attrs]
  (let [path    (pending-path state-dir id)
        current (or (read-record path) {:id id})
        updated (merge current attrs)]
    (fs/mkdirs (fs/parent path))
    (fs/spit path (write-edn updated))
    updated))

(defn read-pending [state-dir id]
  (read-record (pending-path state-dir id)))

(defn read-failed [state-dir id]
  (read-record (failed-path state-dir id)))

(defn delete-pending! [state-dir id]
  (fs/delete (pending-path state-dir id)))

(defn move-to-failed! [state-dir id attrs]
  (let [record (merge (or (read-pending state-dir id) {:id id}) attrs)
        path   (failed-path state-dir id)]
    (fs/mkdirs (fs/parent path))
    (fs/spit path (write-edn record))
    (delete-pending! state-dir id)
    record))

(defn list-pending [state-dir]
  (let [dir (pending-dir state-dir)]
    (if-let [children (fs/children dir)]
      (->> children
           (map #(read-record (str dir "/" %)))
           (remove nil?)
           (sort-by :id)
           vec)
      [])))
