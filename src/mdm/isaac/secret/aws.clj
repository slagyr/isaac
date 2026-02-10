(ns mdm.isaac.secret.aws
  "AWS Secrets Manager implementation for secret retrieval."
  (:require [mdm.isaac.aws :as aws]
            [mdm.isaac.config :as config]
            [mdm.isaac.secret.core :as secret]))

(defn- aws-prefix []
  (get-in config/active [:secret-source :prefix] ""))

(defn- aws-region []
  (get-in config/active [:secret-source :region] "us-west-2"))

(defmethod secret/get-secret :aws [name]
  (aws/get-secret (str (aws-prefix) name) (aws-region)))
