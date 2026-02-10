(ns mdm.isaac.migrations.20260210-1118-config-table
  (:require [c3kit.bucket.jdbc :as jdbc]))

(defn up []
  (jdbc/execute! "CREATE TABLE IF NOT EXISTS config (
    id SERIAL PRIMARY KEY,
    key VARCHAR(255) UNIQUE NOT NULL,
    value TEXT
  )"))

(defn down []
  (jdbc/execute! "DROP TABLE IF EXISTS config"))
