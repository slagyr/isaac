(ns mdm.isaac.goal.core
  "Goal management - convenience functions for working with goal-type thoughts."
  (:require [c3kit.bucket.api :as db]
            [mdm.isaac.thought.core :as thought]))

(defn create!
  "Create a new goal with the given content and embedding.
   Options: :priority (default 5), :status (default :active)"
  ([content embedding] (create! content embedding {}))
  ([content embedding {:keys [priority] :or {priority 5}}]
   (db/tx {:kind      :thought
           :type      :goal
           :status    :active
           :priority  priority
           :content   content
           :embedding embedding})))

(defn find-active
  "Find all goals with :status :active"
  []
  (->> (thought/find-by-type :goal)
       (filter #(= :active (:status %)))))

(defn resolve!
  "Mark a goal as resolved"
  [goal]
  (db/tx (assoc goal :status :resolved)))

(defn abandon!
  "Mark a goal as abandoned"
  [goal]
  (db/tx (assoc goal :status :abandoned)))
