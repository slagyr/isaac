(ns mdm.isaac.schema.full
  (:require [mdm.isaac.schema.thought :as thought]))

(def full-schema
  [thought/thought])

(def by-kind (delay (reduce #(assoc %1 (-> %2 :kind :value) %2) {} full-schema)))

