(ns mdm.isaac.thought.core
  (:require [mdm.isaac.config :as config]))

(defmulti save
  "Save a thought to the configured storage backend.
   Dispatches on (-> config/active :db :impl)"
  (fn [_thought] (-> config/active :db :impl)))
