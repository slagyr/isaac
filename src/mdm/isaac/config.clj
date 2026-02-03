(ns mdm.isaac.config
  (:require [c3kit.apron.app :as app]))


(def postgres-db {:impl   :postgres
                  :dbtype "postgresql"
                  :dbname "dunno"
                  :host   "localhost"
                  :port   5432})

(def bucket-base {:impl         :jdbc
                  :dialect      :postgres
                  :host         "localhost"
                  :port         5432
                  :dbtype       "postgresql"
                  :dbname       "isaac"
                  :full-schema  'mdm.isaac.schema/full
                  :migration-ns 'mdm.isaac.migrations})

(def base
  {
   :log-level  :trace
   :embedding  {:impl :djl}
   :db         postgres-db
   :bucket     bucket-base
   :jwt-secret "PLEASE POPULATE ME IN EACH ENVIRONMENT"
   :host       "localhost"
   :port       8600
   })

(def development
  (merge base
         {:db     (assoc postgres-db :dbname "isaac-dev")
          :bucket (assoc bucket-base :dbname "isaac-dev")}))

(def staging
  (merge base
         {:db     (assoc postgres-db :dbname "isaac-staging")
          :bucket (assoc bucket-base :dbname "isaac-staging")}))

(def production
  (merge base
         {:db     (assoc postgres-db :dbname "isaac-prod")
          :bucket (assoc bucket-base :dbname "isaac-prod")}))

(def environment (app/find-env "c3.env" "C3_ENV"))
(defn development? [] (= "development" environment))
(defn staging? [] (= "staging" environment))
(defn production? [] (= "production" environment))

(def active
  (case environment
    "staging" staging
    "production" production
    development))

(def host (:host active))
(defn link [& parts] (apply str host parts))
(def admin-email "Clean Coders <admin@cleancoders.com>")
