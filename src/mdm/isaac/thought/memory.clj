(ns mdm.isaac.thought.memory
  (:require [mdm.isaac.thought.core :as core]))

(def mem-db (atom {}))
(def mem-id (atom 0))

(defn- mem-ensure-id [thought]
  (if (:id thought)
    thought
    (assoc thought :id (swap! mem-id inc))))

(defmethod core/save :memory [thought]
  (let [thought (-> (assoc thought :kind :thought)
                    mem-ensure-id)]
    (swap! mem-db assoc (:id thought) thought)
    (get @mem-db (:id thought))))
