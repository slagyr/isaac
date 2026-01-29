(ns mdm.isaac.schema.thought
  (:require [c3kit.apron.schema :as s]))

(def thought-types #{:thought :goal :insight :question :share})

(def thought
  {:kind      (s/kind :thought)
   :id        {:type :long}
   :type      {:type :keyword :validate thought-types}
   :content   {:type :string}
   :embedding {:type [:float] :db {:type "vector(768)"}}})
