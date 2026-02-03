(ns mdm.isaac.schema.thought
  (:require [c3kit.apron.schema :as s]))

;; TODO (isaac-eyj) - MDM: delete me as I already exist as mdm.isaac.thought.schema

(def thought-types #{:thought :goal :insight :question :share})
(def goal-statuses #{:active :resolved :abandoned})

(def thought
  {:kind      (s/kind :thought)
   :id        {:type :long}
   :type      {:type :keyword :validate thought-types}
   :status    {:type :keyword :validate goal-statuses}
   :priority  {:type :long}
   :content   {:type :string}
   :embedding {:type [:float] :db {:type "vector(768)"}}
   :read-at   {:type :long}})
