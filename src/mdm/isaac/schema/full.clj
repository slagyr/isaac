(ns mdm.isaac.schema.full
  (:require [mdm.isaac.schema.friend :as friend]
            [mdm.isaac.schema.thought :as thought]))

;; TODO (isaac-eyj) - MDM: delete me as I already exist as mdm.isaac.schema

(def full-schema
  [friend/friend
   thought/thought])

(def by-kind (delay (reduce #(assoc %1 (-> %2 :kind :value) %2) {} full-schema)))

