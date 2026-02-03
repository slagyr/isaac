(ns mdm.isaac.migrations.20260202-2017-embedding-dimension
  (:require [c3kit.bucket.jdbc :as jdbc]))

(defn up []
  ;; Clear existing 768-dimension embeddings - they're incompatible with DJL's 384 dimensions
  ;; Thoughts will need to be re-embedded with the new provider
  (jdbc/execute! "UPDATE thought SET embedding = NULL")
  (jdbc/execute! "ALTER TABLE thought ALTER COLUMN embedding TYPE vector(384)"))

(defn down []
  ;; Clear 384-dimension embeddings before reverting to 768
  (jdbc/execute! "UPDATE thought SET embedding = NULL")
  (jdbc/execute! "ALTER TABLE thought ALTER COLUMN embedding TYPE vector(768)"))
