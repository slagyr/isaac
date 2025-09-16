(ns mdm.isaac.schema.thought
  (:require [c3kit.apron.schema :as s]))

(def thought
  {
   :kind      (s/kind :thought)
   :id        {:type :long}
   :content   {:type :string}
   :embedding {:type [:float] :db {:type "vector(768)"}}
   })
