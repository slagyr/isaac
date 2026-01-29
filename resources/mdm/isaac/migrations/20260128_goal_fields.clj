(ns mdm.isaac.migrations.20260128-goal-fields
  (:require [c3kit.bucket.jdbc :as jdbc]))

(defn up []
  (jdbc/execute! "ALTER TABLE thought ADD COLUMN IF NOT EXISTS status VARCHAR(32)")
  (jdbc/execute! "ALTER TABLE thought ADD COLUMN IF NOT EXISTS priority INTEGER"))

(defn down []
  (jdbc/execute! "ALTER TABLE thought DROP COLUMN IF EXISTS status")
  (jdbc/execute! "ALTER TABLE thought DROP COLUMN IF EXISTS priority"))
