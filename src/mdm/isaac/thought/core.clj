(ns mdm.isaac.thought.core
  (:require [mdm.isaac.config :as config]))

(defmulti save
  "Save a thought to the configured storage backend.
   Dispatches on (-> config/active :db :impl)"
  (fn [_thought] (-> config/active :db :impl)))

(defmulti find-similar
  "Find top N thoughts matching a given embedding vector by cosine similarity.
   Dispatches on (-> config/active :db :impl)"
  (fn [_embedding _limit] (-> config/active :db :impl)))

(defmulti find-by-type
  "Find all thoughts of a given type.
   Dispatches on (-> config/active :db :impl)"
  (fn [_type] (-> config/active :db :impl)))
