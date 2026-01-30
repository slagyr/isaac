(ns mdm.isaac.aws
  (:require [mdm.isaac.config :as config]
            [c3kit.apron.env :as env])
  (:import
    (com.amazonaws.auth AWSStaticCredentialsProvider BasicAWSCredentials InstanceProfileCredentialsProvider)
    (software.amazon.awssdk.regions Region)
    (software.amazon.awssdk.services.secretsmanager SecretsManagerClient)
    (software.amazon.awssdk.services.secretsmanager.model
      GetSecretValueRequest
      GetSecretValueResponse
      InvalidRequestException
      InvalidParameterException)))

(def region "us-west-2")
(def access-key (System/getenv "CC_AWS_ACCESS_KEY"))
(def secret-key (System/getenv "CC_AWS_SECRET_KEY"))

;; TODO - MDM: Use the AWS v2 version of credentials so we don't need both version in the deps.edn.
(def credentials
  (delay
    (if config/development?
      (AWSStaticCredentialsProvider. (BasicAWSCredentials. access-key secret-key))
      (InstanceProfileCredentialsProvider. false))))

(defn create-secrets-manager-client
  [^Region region-obj]
  (-> (SecretsManagerClient/builder)
      (.region region-obj)
      (.build)))

(defn create-secret-request
  [^String secret-name]
  (-> (GetSecretValueRequest/builder)
      (.secretId secret-name)
      (.build)))

(defn -get-secret
  "Retrieves a secret from AWS Secrets Manager.

  Parameters:
  - secret-name: The name of the secret to retrieve.
  - region: The AWS region where the secret is stored.

  Returns:
  - The secret string if retrieval is successful.
  - nil if an error occurs."
  [^String secret-name ^String region]
  (let [region-obj (Region/of region)]
    (with-open [^SecretsManagerClient client (create-secrets-manager-client region-obj)]
      (try
        (let [^GetSecretValueRequest request   (create-secret-request secret-name)
              ^GetSecretValueResponse response (.getSecretValue client request)]
          (.secretString response))
        (catch InvalidRequestException e
          (println (str "The request was invalid: " (.getMessage e)))
          nil)
        (catch InvalidParameterException e
          (println (str "Invalid parameters provided: " (.getMessage e)))
          nil)
        (catch Exception e
          (println (str "An error occurred while retrieving the secret: " (.getMessage e)))
          nil)))))

(def get-secret (memoize -get-secret))
