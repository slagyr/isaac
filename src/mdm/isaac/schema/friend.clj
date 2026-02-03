(ns mdm.isaac.schema.friend
  (:require [c3kit.apron.schema :as s]))

;; TODO (isaac-t3w) - MDM: move to mdm.isaac.friend.schema
(def friend
  {:kind     (s/kind :friend)
   :id       {:type :long}
   :name     {:type :string}
   :metadata {:type :map}})
