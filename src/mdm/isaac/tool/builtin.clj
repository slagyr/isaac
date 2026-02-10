(ns mdm.isaac.tool.builtin
  "Built-in tools for Isaac - goal management, thought creation, and search."
  (:require [c3kit.bucket.api :as db]
            [mdm.isaac.embedding.core :as embedding]
            [mdm.isaac.goal.core :as goal]
            [mdm.isaac.thought.core :as thought]
            [mdm.isaac.tool.core :as tool]))

;; Helper functions

(defn- find-goal-by-id [id]
  (first (filter #(= id (:id %)) (thought/find-by-type :goal))))

;; Tool definitions

(def create-goal-tool
  {:name :create-goal
   :description "Create a new goal for Isaac to work on"
   :params {:content {:type :string :required true}
            :priority {:type :long :required false}}
   :execute (fn [{:keys [content priority]}]
              (if (empty? content)
                {:status :error :message "Content is required"}
                (let [embedding (embedding/text-embedding content)
                      new-goal (goal/create! content embedding {:priority (or priority 5)})]
                  {:status :ok :goal new-goal})))})

(def list-goals-tool
  {:name :list-goals
   :description "List all active goals"
   :params {}
   :execute (fn [_]
              {:status :ok :goals (goal/find-active)})})

(def update-goal-tool
  {:name :update-goal
   :description "Update a goal's status or priority"
   :params {:id {:type :long :required true}
            :status {:type :keyword :required false}
            :priority {:type :long :required false}}
   :execute (fn [{:keys [id status priority]}]
              (if-let [existing (find-goal-by-id id)]
                (let [updated (cond-> existing
                                status (assoc :status status)
                                priority (assoc :priority priority))
                      saved (db/tx updated)]
                  {:status :ok :goal saved})
                {:status :error :message "Goal not found"}))})

(def search-thoughts-tool
  {:name :search-thoughts
   :description "Search for similar thoughts using semantic search"
   :params {:query {:type :string :required true}
            :limit {:type :long :required false}}
   :execute (fn [{:keys [query limit]}]
              (let [embedding (embedding/text-embedding query)
                    results (thought/find-similar embedding (or limit 5))]
                {:status :ok :thoughts results}))})

(def create-thought-tool
  {:name :create-thought
   :description "Create a new thought (insight, question, or share)"
   :params {:content {:type :string :required true}
            :type {:type :keyword :required false}}
   :execute (fn [{:keys [content type]}]
              (let [embedding (embedding/text-embedding content)
                    new-thought (db/tx {:kind :thought
                                        :type (or type :thought)
                                        :content content
                                        :embedding embedding})]
                {:status :ok :thought new-thought}))})

;; Registration

(def all-tools
  [create-goal-tool
   list-goals-tool
   update-goal-tool
   search-thoughts-tool
   create-thought-tool])

(defn register-all!
  "Register all built-in tools."
  []
  (doseq [t all-tools]
    (tool/register! t)))
