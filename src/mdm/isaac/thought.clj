(ns mdm.isaac.thought
  "Thought persistence - re-exports from sub-namespaces for backward compatibility."
  (:require [mdm.isaac.thought.core :as core]
            [mdm.isaac.thought.memory]
            [mdm.isaac.thought.pg :as pg]))

;; Re-export core multimethods
(def save core/save)
(def find-similar core/find-similar)

;; Re-export pg functions (new names)
(def create-database pg/create-database)
(def drop-database pg/drop-database)
(def init pg/init)

;; Backward-compatible aliases (old names)
(def pg-create-database pg/create-database)
(def pg-drop-database pg/drop-database)
(def pg-init pg/init)
