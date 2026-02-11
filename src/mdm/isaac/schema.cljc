(ns mdm.isaac.schema
  (:require [mdm.isaac.friend.schema :as friend]
            [mdm.isaac.setting.schema :as setting]
            [mdm.isaac.thought.schema :as thought]
            [mdm.isaac.user.schema :as user]))

(def full
  (flatten [friend/friend
            setting/config
            thought/all
            user/user]))

(def by-kind (delay (reduce #(assoc %1 (-> %2 :kind :value) %2) {} full)))

