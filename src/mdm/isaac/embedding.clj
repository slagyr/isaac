(ns mdm.isaac.embedding
  (:require [mdm.isaac.embedding.core :as core]
            [mdm.isaac.embedding.djl]
            [mdm.isaac.embedding.ollama]))

;; Re-export the text-embedding multimethod from core for convenience
(def text-embedding core/text-embedding)
