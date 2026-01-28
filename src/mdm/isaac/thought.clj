(ns mdm.isaac.thought
  (:require [c3kit.apron.utilc :as utilc]
            [mdm.isaac.config :as config]
            [next.jdbc :as jdbc]))

(defmulti save (fn [_thought] (-> config/active :db :impl)))

;; region ----- memory -----

(def mem-db (atom {}))
(def mem-id (atom 0))

(defn- mem-ensure-id [thought]
  (if (:id thought)
    thought
    (assoc thought :id (swap! mem-id inc))))

(defmethod save :memory [thought]
  (let [thought (-> (assoc thought :kind :thought)
                    mem-ensure-id)]
    (swap! mem-db assoc (:id thought) thought)
    (get @mem-db (:id thought))))

;; endregion ^^^^^ memory ^^^^^


;; region ----- postgresql -----

;; TODO (isaac-w9a) - MDM: replace me with use of (:db config/active)
(def pg-root {:dbtype "postgresql" :dbname "postgres" :host "localhost" :port 5432})

(defn pg-create-database [db-name]
  (jdbc/execute-one! pg-root [(str "CREATE DATABASE " db-name)]))

(defn pg-drop-database [db-name]
  (jdbc/execute-one! pg-root [(str "DROP DATABASE IF EXISTS " db-name)]))

(def pg-isaac {:dbtype "postgresql" :dbname "isaac" :host "localhost" :port 5432})

(defn pg-init [db]
  (jdbc/execute! db ["CREATE EXTENSION IF NOT EXISTS vector"])
  (jdbc/execute! db ["CREATE TABLE IF NOT EXISTS thought (id SERIAL PRIMARY KEY, content TEXT, embedding vector(768))"]))

(defmethod save :postgres [thought]
  (let [id (:id thought)
        sql (if id
              (format "UPDATE thought SET content = ?, embedding = ?::vector WHERE id = ? RETURNING id, content, embedding")
              (format "INSERT INTO thought (content, embedding) VALUES (?, ?::vector) RETURNING id, content, embedding"))
        result (if id
                 (jdbc/execute-one! pg-isaac [sql (:content thought) (into-array (:embedding thought)) id])
                 (jdbc/execute-one! pg-isaac [sql (:content thought) (into-array (:embedding thought))]))]
    {:kind :thought
     :id (:thought/id result)
     :content (:thought/content result)
     :embedding (utilc/<-edn (.getValue (:thought/embedding result)))}))

;; endregion ^^^^^ postgresql ^^^^^
