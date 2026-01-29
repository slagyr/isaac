(ns mdm.isaac.schema.full
  (:require [mdm.isaac.schema.friend :as friend]
            [mdm.isaac.schema.thought :as thought]))

(def full-schema
  [friend/friend
   thought/thought])

(def by-kind (delay (reduce #(assoc %1 (-> %2 :kind :value) %2) {} full-schema)))

