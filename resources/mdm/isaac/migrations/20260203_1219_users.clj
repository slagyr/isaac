(ns mdm.isaac.migrations.20260203-1219-users
  (:require [c3kit.bucket.jdbc :as jdbc]))

(defn up []
  (jdbc/execute!
    "CREATE TABLE IF NOT EXISTS \"user\" (
      id SERIAL PRIMARY KEY,
      email VARCHAR(255) UNIQUE NOT NULL,
      password VARCHAR(255),
      name VARCHAR(255),
      max_requests_per_hr INTEGER DEFAULT 25,
      confirmation_token UUID,
      recovery_token UUID,
      created_at TIMESTAMP DEFAULT NOW()
    )"))

(defn down []
  (jdbc/execute! "DROP TABLE IF EXISTS \"user\""))
