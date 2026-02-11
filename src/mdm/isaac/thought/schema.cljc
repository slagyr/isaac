(ns mdm.isaac.thought.schema
  (:require [c3kit.apron.schema :as s]))

(def thought-types #{"thought" "goal" "insight" "question" "share"})
(def goal-statuses #{"active" "resolved" "abandoned"})

(def thought
  {:kind              (s/kind :thought)
   :id                {:type :long}
   :type              {:type :string :validate thought-types}
   :status            {:type :string :validate goal-statuses}
   :priority          {:type :long}
   :content           {:type :string}
   :embedding         {:type [:float] :db {:type "vector(384)"}}
   :read-at           {:type :long :db {:name "read_at"}}
   :seen-count        {:type :long :db {:name "seen_count"}}
   :source-message-id {:type :long :db {:name "source_message_id"}}})

(def all [thought])
