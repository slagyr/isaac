(ns mdm.isaac.goal
  "Goal management - re-exports from sub-namespaces for backward compatibility."
  (:require [mdm.isaac.goal.core :as core]))

;; Re-export from core for backward compatibility
(def create! core/create!)
(def find-active core/find-active)
(def resolve! core/resolve!)
(def abandon! core/abandon!)
