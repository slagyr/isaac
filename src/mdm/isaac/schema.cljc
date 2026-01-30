(ns mdm.isaac.schema
  (:require [mdm.isaac.schema.friend :as friend]
            [mdm.isaac.thought.schema :as thought]
            [mdm.isaac.user.schema :as user]))

(def full
  (flatten [friend/friend
            thought/all
            user/user]))

(def by-kind (delay (reduce #(assoc %1 (-> %2 :kind :value) %2) {} full)))

