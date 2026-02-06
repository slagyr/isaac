(ns mdm.isaac.goal.ws
  "WebSocket handlers for goals."
  (:require [c3kit.bucket.api :as db]
            [c3kit.wire.apic :as apic]
            [mdm.isaac.embedding.core :as embedding]
            [mdm.isaac.goal.core :as goal]
            [mdm.isaac.thought.core :as thought]))

(defn- find-goal-by-id [id]
  (first (filter #(= id (:id %)) (thought/find-by-type :goal))))

(defn ws-list
  "Returns all active goals."
  [_message]
  (let [goals (goal/find-active)]
    (prn "goals: " goals)
    (apic/ok goals)))

(defn ws-add
  "Creates a new goal with content and optional priority."
  [{:keys [params]}]
  (let [{:keys [content priority]} params]
    (if (empty? content)
      (apic/fail)
      (let [embedding (embedding/text-embedding content)
            new-goal (goal/create! content embedding {:priority (or priority 5)})]
        (apic/ok new-goal)))))

(defn ws-update
  "Updates a goal's status and/or priority."
  [{:keys [params]}]
  (let [{:keys [id status priority]} params]
    (if-let [goal (find-goal-by-id id)]
      (let [updated (cond-> goal
                      status (assoc :status status)
                      priority (assoc :priority priority))
            saved (db/tx updated)]
        (apic/ok saved))
      (apic/fail))))
