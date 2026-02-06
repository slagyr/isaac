(ns mdm.isaac.share
  "Sharing system - re-exports from sub-namespaces for backward compatibility."
  (:require [mdm.isaac.share.core :as core]))

;; Re-export from core for backward compatibility
(def create! core/create!)
(def unread core/unread)
(def acknowledge! core/acknowledge!)
