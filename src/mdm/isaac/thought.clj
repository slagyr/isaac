(ns mdm.isaac.thought
  "Thought persistence - re-exports from sub-namespaces for backward compatibility."
  (:require [mdm.isaac.thought.core :as core]
            [mdm.isaac.thought.memory :as memory]
            [mdm.isaac.thought.pg :as pg]))

;; Re-export core multimethods
(def save core/save)
(def find-similar core/find-similar)
(def find-by-type core/find-by-type)

;; Re-export memory functions
(def memory-clear! memory/clear!)

;; Re-export pg functions (new names)
(def create-database pg/create-database)
(def drop-database pg/drop-database)
(def init pg/init)
(def pg-clear! pg/clear!)

;; Backward-compatible aliases (old names)
(def pg-create-database pg/create-database)
(def pg-drop-database pg/drop-database)
(def pg-init pg/init)
