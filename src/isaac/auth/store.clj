(ns isaac.auth.store
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]))

(defn save-tokens!
  "Save OAuth tokens for a provider to auth.json in the given directory."
  [auth-dir provider-name tokens]
  (let [auth-file (io/file auth-dir "auth.json")
        existing  (if (.exists auth-file)
                    (json/parse-string (slurp auth-file) true)
                    {})
        entry     {:type    "oauth"
                   :access  (:access_token tokens)
                   :refresh (:refresh_token tokens)
                   :expires (+ (System/currentTimeMillis) (* (:expires_in tokens) 1000))}
        updated   (assoc existing (keyword provider-name) entry)]
    (.mkdirs (io/file auth-dir))
    (spit auth-file (json/generate-string updated {:pretty true}))))

(defn load-tokens
  "Load OAuth tokens for a provider from auth.json. Returns nil if not found."
  [auth-dir provider-name]
  (let [auth-file (io/file auth-dir "auth.json")]
    (when (.exists auth-file)
      (let [data (json/parse-string (slurp auth-file) true)]
        (get data (keyword provider-name))))))

(defn token-expired?
  "Check if a token map has expired."
  [tokens]
  (let [expires (:expires tokens)]
    (or (nil? expires)
        (<= expires (System/currentTimeMillis)))))
