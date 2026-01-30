(ns mdm.isaac.ws
  "WebSocket API handlers for Isaac server."
  (:require [c3kit.wire.apic :as apic]
            [mdm.isaac.embedding.core :as embedding]
            [mdm.isaac.goal :as goal]
            [mdm.isaac.share :as share]
            [mdm.isaac.thought :as thought]))

;; Goal handlers

(defn goals-list
  "Returns all active goals."
  [_message]
  (apic/ok (goal/find-active)))

(defn goals-add
  "Creates a new goal with content and optional priority."
  [{:keys [params]}]
  (let [{:keys [content priority]} params]
    (if (empty? content)
      (apic/fail)
      (let [embedding (embedding/text-embedding content)
            new-goal (goal/create! content embedding {:priority (or priority 5)})]
        (apic/ok new-goal)))))

(defn- find-goal-by-id [id]
  (first (filter #(= id (:id %)) (thought/find-by-type :goal))))

(defn goals-update
  "Updates a goal's status and/or priority."
  [{:keys [params]}]
  (let [{:keys [id status priority]} params]
    (if-let [goal (find-goal-by-id id)]
      (let [updated (cond-> goal
                      status (assoc :status status)
                      priority (assoc :priority priority))
            saved (thought/save updated)]
        (apic/ok saved))
      (apic/fail))))

;; Thought handlers

(def thought-types [:goal :insight :question :share])

(defn- all-thoughts
  "Get all thoughts of all types."
  []
  (mapcat thought/find-by-type thought-types))

(defn thoughts-recent
  "Returns recent thoughts with optional limit."
  [{:keys [params]}]
  (let [limit (get params :limit 20)
        thoughts (->> (all-thoughts)
                      (sort-by :id >)
                      (take limit))]
    (apic/ok (vec thoughts))))

(defn thoughts-search
  "Search thoughts by query text using embedding similarity."
  [{:keys [params]}]
  (let [{:keys [query limit]} params]
    (if (empty? query)
      (apic/fail)
      (let [query-embedding (embedding/text-embedding query)
            results (thought/find-similar query-embedding (or limit 10))]
        (apic/ok (vec results))))))

;; Share handlers

(defn shares-unread
  "Returns all unread shares."
  [_message]
  (apic/ok (share/unread)))

(defn shares-ack
  "Acknowledges a share by id."
  [{:keys [params]}]
  (let [shares (thought/find-by-type :share)
        share (first (filter #(= (:id params) (:id %)) shares))]
    (if share
      (do
        (share/acknowledge! share)
        (apic/ok))
      (apic/fail))))

;; Handler registration map for c3kit.wire.websocket
;; Maps message kinds to handler function symbols

(def handlers
  {:goals/list      'mdm.isaac.ws/goals-list
   :goals/add       'mdm.isaac.ws/goals-add
   :goals/update    'mdm.isaac.ws/goals-update
   :thoughts/recent 'mdm.isaac.ws/thoughts-recent
   :thoughts/search 'mdm.isaac.ws/thoughts-search
   :shares/unread   'mdm.isaac.ws/shares-unread
   :shares/ack      'mdm.isaac.ws/shares-ack})
