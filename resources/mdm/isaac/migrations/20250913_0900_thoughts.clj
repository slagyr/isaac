(ns mdm.isaac.migrations.20250913-0900-thoughts
  (:require [c3kit.bucket.jdbc :as jdbc]))

(defn up []
  (jdbc/execute! "CREATE EXTENSION IF NOT EXISTS vector")
  (jdbc/execute! "CREATE TABLE IF NOT EXISTS thought (id SERIAL PRIMARY KEY, content TEXT, embedding vector(768))"))

(defn down []
  (jdbc/execute! "DROP TABLE IF EXISTS thought"))
