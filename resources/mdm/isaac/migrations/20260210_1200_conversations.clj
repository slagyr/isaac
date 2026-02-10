(ns mdm.isaac.migrations.20260210-1200-conversations
  (:require [c3kit.bucket.jdbc :as jdbc]))

(defn up []
  (jdbc/execute!
    "CREATE TABLE IF NOT EXISTS conversation (
      id SERIAL PRIMARY KEY,
      user_id INTEGER NOT NULL REFERENCES \"user\"(id),
      started_at TIMESTAMP DEFAULT NOW(),
      updated_at TIMESTAMP DEFAULT NOW(),
      status VARCHAR(50) DEFAULT 'active'
    )")
  (jdbc/execute!
    "CREATE TABLE IF NOT EXISTS message (
      id SERIAL PRIMARY KEY,
      conversation_id INTEGER NOT NULL REFERENCES conversation(id),
      role VARCHAR(50) NOT NULL,
      content TEXT NOT NULL,
      created_at TIMESTAMP DEFAULT NOW(),
      thought_ids INTEGER[] DEFAULT '{}'
    )"))

(defn down []
  (jdbc/execute! "DROP TABLE IF EXISTS message")
  (jdbc/execute! "DROP TABLE IF EXISTS conversation"))
