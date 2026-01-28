(ns mdm.isaac.embedding
  (:require [mdm.isaac.embedding.core :as core]
            [mdm.isaac.embedding.djl]
            [mdm.isaac.embedding.ollama]))

;; Re-export the embed multimethod from core for backward compatibility
;; TODO - MDM: rename to text-embedding
(def embed core/embed)
