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
  (jdbc/execute! db ["CREATE TABLE IF NOT EXISTS thought (id SERIAL PRIMARY KEY, content TEXT, embedding vector(768))"]))

(defn- result->thought [result]
  {:kind :thought
   :id (:thought/id result)
   :content (:thought/content result)
   :embedding (utilc/<-edn (.getValue (:thought/embedding result)))})

(defmethod core/save :postgres [thought]
  (let [db (:db config/active)
        id (:id thought)
        sql (if id
              (format "UPDATE thought SET content = ?, embedding = ?::vector WHERE id = ? RETURNING id, content, embedding")
              (format "INSERT INTO thought (content, embedding) VALUES (?, ?::vector) RETURNING id, content, embedding"))
        result (if id
                 (jdbc/execute-one! db [sql (:content thought) (into-array (:embedding thought)) id])
                 (jdbc/execute-one! db [sql (:content thought) (into-array (:embedding thought))]))]
    (result->thought result)))

(defmethod core/find-similar :postgres [embedding limit]
  (let [db (:db config/active)
        sql "SELECT id, content, embedding FROM thought ORDER BY embedding <=> ?::vector LIMIT ?"
        results (jdbc/execute! db [sql (into-array embedding) limit])]
    (mapv result->thought results)))
