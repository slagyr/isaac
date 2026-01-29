(ns mdm.isaac.thought.pg
  (:require [c3kit.apron.utilc :as utilc]
            [mdm.isaac.config :as config]
            [mdm.isaac.thought.core :as core]
            [next.jdbc :as jdbc]))

(defn- pg-root []
  (assoc (:db config/active) :dbname "postgres"))

(defn create-database [db-name]
  (jdbc/execute-one! (pg-root) [(str "CREATE DATABASE " db-name)]))

(defn drop-database [db-name]
  (jdbc/execute-one! (pg-root) [(str "DROP DATABASE IF EXISTS " db-name)]))

(defn init [db]
  (jdbc/execute! db ["CREATE EXTENSION IF NOT EXISTS vector"])
  (jdbc/execute! db ["CREATE TABLE IF NOT EXISTS thought (id SERIAL PRIMARY KEY, type VARCHAR(32) DEFAULT 'thought', status VARCHAR(32), priority INTEGER, content TEXT, embedding vector(768))"]))

(defn clear! []
  (jdbc/execute! (:db config/active) ["TRUNCATE TABLE thought RESTART IDENTITY"]))

(defn- result->thought [result]
  (cond-> {:kind :thought
           :id (:thought/id result)
           :type (keyword (:thought/type result))
           :content (:thought/content result)
           :embedding (utilc/<-edn (.getValue (:thought/embedding result)))}
    (:thought/status result) (assoc :status (keyword (:thought/status result)))
    (:thought/priority result) (assoc :priority (:thought/priority result))))

(defmethod core/save :postgres [thought]
  (let [db (:db config/active)
        id (:id thought)
        thought-type (name (or (:type thought) :thought))
        status (some-> (:status thought) name)
        priority (:priority thought)
        sql (if id
              "UPDATE thought SET type = ?, status = ?, priority = ?, content = ?, embedding = ?::vector WHERE id = ? RETURNING id, type, status, priority, content, embedding"
              "INSERT INTO thought (type, status, priority, content, embedding) VALUES (?, ?, ?, ?, ?::vector) RETURNING id, type, status, priority, content, embedding")
        result (if id
                 (jdbc/execute-one! db [sql thought-type status priority (:content thought) (into-array (:embedding thought)) id])
                 (jdbc/execute-one! db [sql thought-type status priority (:content thought) (into-array (:embedding thought))]))]
    (result->thought result)))

(defmethod core/find-similar :postgres [embedding limit]
  (let [db (:db config/active)
        sql "SELECT id, type, status, priority, content, embedding FROM thought ORDER BY embedding <=> ?::vector LIMIT ?"
        results (jdbc/execute! db [sql (into-array embedding) limit])]
    (mapv result->thought results)))

(defmethod core/find-by-type :postgres [thought-type]
  (let [db (:db config/active)
        sql "SELECT id, type, status, priority, content, embedding FROM thought WHERE type = ?"
        results (jdbc/execute! db [sql (name thought-type)])]
    (mapv result->thought results)))
