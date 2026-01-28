(ns mdm.isaac.config
  (:require [c3kit.apron.app :as app]))

(def base
  {
   :log-level  :trace
   :embeddings {:impl :djl}
   })

(def development
  (merge base
         {}))

(def staging
  (merge base
         {}))

(def production
  (merge base
         {}))

(def environment (app/find-env "c3.env" "C3_ENV"))
(defn development? [] (= "development" environment))
(defn staging? [] (= "staging" environment))
(defn production? [] (= "production" environment))

(def active
  (case environment
    "staging" staging
    "production" production
    development))
