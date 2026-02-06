(ns mdm.isaac.friend.schema
  (:require [c3kit.apron.schema :as s]))

(def friend
  {:kind     (s/kind :friend)
   :id       {:type :long}
   :name     {:type :string}
   :metadata {:type :map}})
