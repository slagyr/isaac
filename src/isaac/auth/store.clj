(ns isaac.auth.store
  (:require
    [cheshire.core :as json]
    [isaac.session.fs :as fs]))

(defn- auth-path [auth-dir]
  (str auth-dir "/auth.json"))

(defn- read-auth [auth-dir]
  (let [path (auth-path auth-dir)]
    (if (fs/file-exists? fs/*fs* path)
      (json/parse-string (fs/read-file fs/*fs* path) true)
      {})))

(defn- write-auth! [auth-dir data]
  (let [path (auth-path auth-dir)]
    (fs/make-dirs fs/*fs* path)
    (fs/write-file fs/*fs* path (json/generate-string data {:pretty true}))))

(defn save-tokens!
  "Save OAuth tokens for a provider to auth.json in the given directory."
  [auth-dir provider-name tokens]
  (let [existing  (read-auth auth-dir)
        entry     {:type    "oauth"
                   :access  (:access_token tokens)
                   :id-token (:id_token tokens)
                   :refresh (:refresh_token tokens)
                   :expires (+ (System/currentTimeMillis) (* (:expires_in tokens) 1000))}
        updated   (assoc existing (keyword provider-name) entry)]
    (write-auth! auth-dir updated)))

(defn save-api-key!
  "Save API key credentials for a provider to auth.json in the given directory."
  [auth-dir provider-name api-key]
  (let [existing (read-auth auth-dir)
        entry    {:type "api-key" :apiKey api-key}
        updated  (assoc existing (keyword provider-name) entry)]
    (write-auth! auth-dir updated)))

(defn load-tokens
  "Load OAuth tokens for a provider from auth.json. Returns nil if not found."
  [auth-dir provider-name]
  (let [data (read-auth auth-dir)]
    (get data (keyword provider-name))))

(defn token-expired?
  "Check if a token map has expired."
  [tokens]
  (let [expires (:expires tokens)]
    (or (nil? expires)
        (<= expires (System/currentTimeMillis)))))
