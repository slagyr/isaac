(ns mdm.isaac.schema
  (:require [mdm.isaac.schema.friend :as friend]
            [mdm.isaac.thought.schema :as thought]))

(def full-schema
  [friend/friend
   thought/thought])

(def by-kind (delay (reduce #(assoc %1 (-> %2 :kind :value) %2) {} full-schema)))
