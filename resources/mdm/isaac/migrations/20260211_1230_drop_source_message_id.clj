(ns mdm.isaac.migrations.20260211-1230-drop-source-message-id
  (:require [c3kit.bucket.jdbc :as jdbc]))

(defn up []
  (jdbc/execute! "ALTER TABLE thought DROP COLUMN IF EXISTS source_message_id"))

(defn down []
  (jdbc/execute! "ALTER TABLE thought ADD COLUMN IF NOT EXISTS source_message_id INTEGER"))
