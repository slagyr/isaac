(ns mdm.isaac.thought
  "Thought persistence - re-exports from sub-namespaces for backward compatibility."
  (:require [mdm.isaac.thought.core :as core]))

;; Re-export from core for backward compatibility
(def find-by-type core/find-by-type)
(def find-similar core/find-similar)
