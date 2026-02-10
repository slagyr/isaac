(ns mdm.isaac.migrations.20260210-1112-seen-count
  (:require [c3kit.bucket.jdbc :as jdbc]))

(defn up []
  (jdbc/execute! "ALTER TABLE thought ADD COLUMN IF NOT EXISTS seen_count INTEGER DEFAULT 1"))

(defn down []
  (jdbc/execute! "ALTER TABLE thought DROP COLUMN IF EXISTS seen_count"))
