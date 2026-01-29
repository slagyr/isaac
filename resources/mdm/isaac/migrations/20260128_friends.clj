(ns mdm.isaac.migrations.20260128-friends
  (:require [c3kit.bucket.jdbc :as jdbc]))

(defn up []
  (jdbc/execute! "CREATE TABLE IF NOT EXISTS friend (id SERIAL PRIMARY KEY, name VARCHAR(255) NOT NULL, metadata JSONB DEFAULT '{}')")
  (jdbc/execute! "INSERT INTO friend (name, metadata) VALUES ('Micah', '{\"role\": \"creator\"}') ON CONFLICT DO NOTHING"))

(defn down []
  (jdbc/execute! "DROP TABLE IF EXISTS friend"))
