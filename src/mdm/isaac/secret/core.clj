(ns mdm.isaac.secret.core
  "Secret retrieval abstraction - configurable providers via multimethod dispatch."
  (:require [c3kit.apron.env :as env]
            [mdm.isaac.config :as config]))

(defn secret-impl
  "Returns the configured secret source implementation keyword."
  []
  (get-in config/active [:secret-source :impl] :env))

(defmulti get-secret
  "Retrieve a secret by name using the configured provider.
   Returns the secret value as a string, or nil if not found."
  (fn [_name] (secret-impl)))

(defmethod get-secret :env [name]
  (env/env name))

(defmethod get-secret :default [_name]
  (throw (ex-info "Unknown secret source" {:impl (secret-impl)})))
