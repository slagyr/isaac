(ns mdm.isaac.config
  (:require [c3kit.apron.app :as app]))


(def postgres-db {:impl   :postgres
                  :dbtype "postgresql"
                  :dbname "dunno"
                  :host   "localhost"
                  :port   5432})

(def base
  {
   :log-level  :trace
   :embeddings {:impl :djl}
   :db         postgres-db
   })

(def development
  (merge base
         {:db (assoc postgres-db :dbname "isaac-dev")}))

(def staging
  (merge base
         {:db (assoc postgres-db :dbname "isaac-staging")}))

(def production
  (merge base
         {:db (assoc postgres-db :dbname "isaac-prod")}))

(def environment (app/find-env "c3.env" "C3_ENV"))
(defn development? [] (= "development" environment))
(defn staging? [] (= "staging" environment))
(defn production? [] (= "production" environment))

(def active
  (case environment
    "staging" staging
    "production" production
    development))
