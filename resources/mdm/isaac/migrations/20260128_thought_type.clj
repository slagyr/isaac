(ns mdm.isaac.migrations.20260128-thought-type
  (:require [c3kit.bucket.jdbc :as jdbc]))

(defn up []
  (jdbc/execute! "ALTER TABLE thought ADD COLUMN IF NOT EXISTS type VARCHAR(32) DEFAULT 'thought'"))

(defn down []
  (jdbc/execute! "ALTER TABLE thought DROP COLUMN IF EXISTS type"))
