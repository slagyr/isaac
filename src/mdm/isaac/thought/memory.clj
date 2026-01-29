(ns mdm.isaac.thought.memory
  (:require [mdm.isaac.thought.core :as core]))

(def mem-db (atom {}))
(def mem-id (atom 0))

(defn clear! []
  (clojure.core/reset! mem-db {})
  (clojure.core/reset! mem-id 0))

(defn- mem-ensure-id [thought]
  (if (:id thought)
    thought
    (assoc thought :id (swap! mem-id inc))))

(defmethod core/save :memory [thought]
  (let [thought (-> thought
                    (assoc :kind :thought)
                    (update :type #(or % :thought))
                    mem-ensure-id)]
    (swap! mem-db assoc (:id thought) thought)
    (get @mem-db (:id thought))))

(defn- dot-product [v1 v2]
  (reduce + (map * v1 v2)))

(defn- magnitude [v]
  (Math/sqrt (reduce + (map #(* % %) v))))

(defn- cosine-similarity [v1 v2]
  (let [mag1 (magnitude v1)
        mag2 (magnitude v2)]
    (if (or (zero? mag1) (zero? mag2))
      0.0
      (/ (dot-product v1 v2) (* mag1 mag2)))))

(defmethod core/find-similar :memory [embedding limit]
  (->> (vals @mem-db)
       (map (fn [thought]
              (assoc thought :similarity (cosine-similarity embedding (:embedding thought)))))
       (sort-by :similarity >)
       (take limit)
       (map #(dissoc % :similarity))))

(defmethod core/find-by-type :memory [thought-type]
  (->> (vals @mem-db)
       (filter #(= thought-type (:type %)))
       vec))
