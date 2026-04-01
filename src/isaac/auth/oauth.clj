(ns isaac.auth.oauth
  (:require
    [babashka.http-client :as http]
    [cheshire.core :as json]
    [clojure.java.io :as io]))

;; region ----- Credential Reading -----

(defn- credentials-path []
  (str (System/getProperty "user.home") "/.claude/.credentials.json"))

(defn read-credentials
  "Read Claude Code OAuth credentials. Returns map with :accessToken,
   :refreshToken, :expiresAt or nil if not found."
  [& [{:keys [path]}]]
  (let [path (or path (credentials-path))
        f    (io/file path)]
    (when (.exists f)
      (let [data (json/parse-string (slurp f) true)]
        (:claudeAiOauth data)))))

(defn- write-credentials! [creds & [{:keys [path]}]]
  (let [path (or path (credentials-path))
        f    (io/file path)
        data (if (.exists f)
               (json/parse-string (slurp f) true)
               {})]
    (io/make-parents path)
    (spit path (json/generate-string (assoc data :claudeAiOauth creds)))))

;; endregion ^^^^^ Credential Reading ^^^^^

;; region ----- Token Refresh -----

(defn- token-expired? [{:keys [expiresAt]}]
  (let [now (System/currentTimeMillis)]
    (< expiresAt now)))

(defn- refresh-token!
  "Refresh an OAuth token using the refresh token.
   Returns updated credentials or {:error ...}."
  [{:keys [refreshToken]} & [{:keys [path]}]]
  (try
    (let [resp (http/post "https://console.anthropic.com/v1/oauth/token"
                          {:body    (json/generate-string
                                      {:grant_type    "refresh_token"
                                       :refresh_token refreshToken
                                       :client_id     "claude-code"})
                           :headers {"Content-Type" "application/json"}
                           :timeout 10000
                           :throw   false})]
      (if (>= (:status resp) 400)
        {:error :oauth-refresh-failed :message "OAuth refresh failed"}
        (let [body  (json/parse-string (:body resp) true)
              creds {:accessToken  (:access_token body)
                     :refreshToken (or (:refresh_token body) refreshToken)
                     :expiresAt    (+ (System/currentTimeMillis)
                                      (* 1000 (or (:expires_in body) 3600)))}]
          (write-credentials! creds {:path path})
          creds)))
    (catch Exception e
      {:error :oauth-refresh-failed :message (.getMessage e)})))

;; endregion ^^^^^ Token Refresh ^^^^^

;; region ----- Public API -----

(defn resolve-token
  "Resolve a valid OAuth access token.
   Reads credentials, refreshes if expired.
   Returns {:accessToken ...} or {:error ...}."
  [& [opts]]
  (let [creds (read-credentials opts)]
    (cond
      (nil? creds)
      {:error :no-oauth-credentials :message "No OAuth credentials found"}

      (token-expired? creds)
      (let [refreshed (refresh-token! creds opts)]
        (if (:error refreshed)
          refreshed
          refreshed))

      :else
      creds)))

;; endregion ^^^^^ Public API ^^^^^
