(ns mdm.isaac.thought.schema
  (:require [c3kit.apron.schema :as s]))

(def thought-types #{:thought :goal :insight :question :share})
(def goal-statuses #{:active :resolved :abandoned})

(def thought
  {:kind      (s/kind :thought)
   :id        {:type :long}
   :type      {:type :keyword :validate thought-types}
   :status    {:type :keyword :validate goal-statuses}
   :priority  {:type :long}
   :content   {:type :string}
   :embedding   {:type [:float] :db {:type "vector(384)"}}
   :read-at           {:type :long}
   :seen-count        {:type :long}
   :source-message-id {:type :long}})

(def all [thought])
