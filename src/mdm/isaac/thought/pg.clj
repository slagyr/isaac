(ns mdm.isaac.thought.pg
  (:require [c3kit.apron.schema :as schema]
            [c3kit.bucket.api :as db]
            [c3kit.bucket.jdbc :as bucket-jdbc]
            [mdm.isaac.config :as config]
            [mdm.isaac.schema.full :as full]
            [mdm.isaac.thought.core :as core]
            [next.jdbc :as jdbc]))

;; Raw JDBC operations for database management (create/drop database, init schema)
;; These operations happen outside bucket's scope

(defn- pg-root []
  (assoc (:db config/active) :dbname "postgres"))

(defn create-database [db-name]
  (jdbc/execute-one! (pg-root) [(str "CREATE DATABASE " db-name)]))

(defn drop-database [db-name]
  (jdbc/execute-one! (pg-root) [(str "DROP DATABASE IF EXISTS " db-name)]))

(defn init [db]
  (jdbc/execute! db ["CREATE EXTENSION IF NOT EXISTS vector"])
  (jdbc/execute! db ["CREATE TABLE IF NOT EXISTS thought (id SERIAL PRIMARY KEY, type VARCHAR(32) DEFAULT 'thought', status VARCHAR(32), priority INTEGER, content TEXT, embedding vector(768), read_at int8)"]))

;; Bucket database management

(defonce ^:private bucket-db (atom nil))

(defn- bucket-config []
  {:impl    :jdbc
   :dialect :postgres
   :host    (-> config/active :db :host)
   :port    (-> config/active :db :port)
   :dbtype  "postgresql"
   :dbname  (-> config/active :db :dbname)})

(defn- get-bucket []
  (or @bucket-db
      (let [schemas (map schema/normalize-schema full/full-schema)
            bucket  (db/create-db (bucket-config) schemas)]
        (reset! bucket-db bucket)
        bucket)))

(defn reset-bucket!
  "Reset the cached bucket instance. Useful for tests when database changes."
  []
  (when-let [b @bucket-db]
    (db/close b))
  (reset! bucket-db nil))

(defn clear! []
  (bucket-jdbc/execute! (get-bucket) ["TRUNCATE TABLE thought RESTART IDENTITY"]))

;; Bucket-based CRUD operations

(defmethod core/save :postgres [thought]
  (let [bucket (get-bucket)
        thought (-> thought
                    (assoc :kind :thought)
                    (update :type #(or % :thought))
                    (update :embedding vec))]
    (db/tx- bucket thought)))

(defmethod core/find-similar :postgres [embedding limit]
  (let [bucket (get-bucket)]
    (db/find- bucket :thought
              :order-by {:embedding ['<=> (vec embedding)]}
              :take limit)))

(defmethod core/find-by-type :postgres [thought-type]
  (let [bucket (get-bucket)]
    (db/find- bucket :thought :where [[:type thought-type]])))
