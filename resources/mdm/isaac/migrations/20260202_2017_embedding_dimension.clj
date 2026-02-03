(ns mdm.isaac.migrations.20260202-2017-embedding-dimension
  (:require [c3kit.bucket.jdbc :as jdbc]))

(defn up []
  (jdbc/execute! "ALTER TABLE thought ALTER COLUMN embedding TYPE vector(384)"))

(defn down []
  (jdbc/execute! "ALTER TABLE thought ALTER COLUMN embedding TYPE vector(768)"))
