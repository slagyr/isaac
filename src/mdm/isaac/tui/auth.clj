(ns mdm.isaac.tui.auth
  "Authentication for Isaac terminal client.
   Handles login via HTTP and token management."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [org.httpkit.client :as http])
  (:import [java.net URI]
           [java.util Base64]))

(defn login
  "Attempts to login with email and password.
   Returns {:ok true :token <jwt>} on success, {:ok false :error <msg>} on failure."
  [base-url email password]
  (try
    (let [response @(http/post (str base-url "/user/jwt")
                               {:form-params {:email email :password password}})]
      (if (= 200 (:status response))
        {:ok true :token (:body response)}
        {:ok false :error (or (:body response) "Login failed")}))
    (catch Exception e
      {:ok false :error (.getMessage e)})))

(defn ws-uri->http-base
  "Converts ws://host:port/path to http://host:port"
  [ws-uri]
  (let [uri (URI. ws-uri)]
    (str "http://" (.getHost uri) ":" (.getPort uri))))

;; Token persistence

(defn isaac-dir
  "Returns the path to ~/.isaac/ directory."
  []
  (str (System/getProperty "user.home") "/.isaac"))

(defn- token-path
  "Returns the path to the JWT token file."
  []
  (str (isaac-dir) "/jwt"))

(defn save-token!
  "Saves JWT token to ~/.isaac/jwt"
  [token]
  (let [dir (io/file (isaac-dir))]
    (when-not (.exists dir)
      (.mkdirs dir))
    (spit (token-path) token)))

(defn load-token
  "Loads JWT token from ~/.isaac/jwt. Returns nil if file doesn't exist."
  []
  (let [f (io/file (token-path))]
    (when (.exists f)
      (str/trim (slurp f)))))

(defn delete-token!
  "Deletes the JWT token file."
  []
  (let [f (io/file (token-path))]
    (when (.exists f)
      (.delete f))))

;; Token validation

(defn- decode-jwt-payload
  "Decodes the payload portion of a JWT. Returns nil on error."
  [token]
  (try
    (when (and token (string? token))
      (let [parts   (str/split token #"\.")
            payload (second parts)
            decoded (String. (.decode (Base64/getUrlDecoder) payload) "UTF-8")]
        (json/read-str decoded :key-fn keyword)))
    (catch Exception _ nil)))

(defn token-valid?
  "Returns true if token is not nil, well-formed, and not expired."
  [token]
  (if-let [payload (decode-jwt-payload token)]
    (let [exp (:exp payload)
          now (quot (System/currentTimeMillis) 1000)]
      (and exp (> exp now)))
    false))

(defn client-id
  "Extracts client-id from JWT token."
  [token]
  (-> token decode-jwt-payload :client-id))
