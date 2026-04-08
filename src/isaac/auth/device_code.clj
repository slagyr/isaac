(ns isaac.auth.device-code
  (:require
    [babashka.http-client :as http]
    [cheshire.core :as json]
    [clojure.string :as str]))

;; region ----- Constants -----

(def client-id "app_EMoamEEZ73f0CkXaXp7hrann")
(def base-url "https://auth.openai.com")
(def verification-url (str base-url "/codex/device"))

;; endregion ^^^^^ Constants ^^^^^

;; region ----- HTTP Helpers -----

(defn -post-json!
  "POST JSON and return parsed response. Seam for testing."
  [url headers body]
  (try
    (let [resp (http/post url {:body    (json/generate-string body)
                               :headers (merge {"Content-Type" "application/json"
                                                "Accept"       "application/json"} headers)
                               :timeout 30000
                               :throw   false})]
      (let [parsed (json/parse-string (:body resp) true)]
        (if (>= (:status resp) 400)
          {:error  (if (#{403 404} (:status resp)) :pending :api-error)
           :status (:status resp)
           :body   parsed}
          parsed)))
    (catch Exception e
      {:error :unknown :message (.getMessage e)})))

(defn -post-form!
  "POST form-encoded data and return parsed JSON response. Seam for testing."
  [url body]
  (try
    (let [form-body (->> body
                         (map (fn [[k v]] (str (java.net.URLEncoder/encode (str k) "UTF-8")
                                               "="
                                               (java.net.URLEncoder/encode (str v) "UTF-8"))))
                         (clojure.string/join "&"))
          resp      (http/post url {:body    form-body
                                    :headers {"Content-Type" "application/x-www-form-urlencoded"}
                                    :timeout 30000
                                    :throw   false})]
      (let [parsed (json/parse-string (:body resp) true)]
        (if (>= (:status resp) 400)
          {:error  :api-error
           :status (:status resp)
           :body   parsed}
          parsed)))
    (catch Exception e
      {:error :unknown :message (.getMessage e)})))

;; endregion ^^^^^ HTTP Helpers ^^^^^

;; region ----- Device Code Flow -----

(defn request-user-code!
  "Step 1: Request a device code and user code from OpenAI."
  []
  (-post-json! (str base-url "/api/accounts/deviceauth/usercode")
               {}
               {"client_id" client-id}))

(defn poll-for-auth!
  "Step 2: Poll for authorization. Returns auth code response or error.
   interval-ms is the polling interval in milliseconds (0 for tests)."
  [device-auth-id user-code interval-ms]
  (let [url  (str base-url "/api/accounts/deviceauth/token")
        body {"device_auth_id" device-auth-id
              "user_code"      user-code}]
    (loop [elapsed 0]
      (when (pos? interval-ms)
        (Thread/sleep interval-ms))
      (let [result (-post-json! url {} body)]
        (cond
          (not (:error result))        result
          (= :pending (:error result)) (if (< elapsed (* 15 60 1000))
                                         (recur (+ elapsed (max interval-ms 1)))
                                         {:error :timeout :message "Device code expired after 15 minutes"})
          :else                        result)))))

(defn exchange-tokens!
  "Step 3: Exchange authorization code for access/refresh tokens."
  [authorization-code code-verifier]
  (-post-form! (str base-url "/oauth/token")
               {"grant_type"    "authorization_code"
                "client_id"     client-id
                "code"          authorization-code
                "code_verifier" code-verifier
                "redirect_uri"  (str base-url "/deviceauth/callback")}))

(defn exchange-api-key!
  "Step 4: Exchange the id_token for an OpenAI API-style bearer token."
  [id-token]
  (-post-form! (str base-url "/oauth/token")
               {"grant_type"         "urn:ietf:params:oauth:grant-type:token-exchange"
                "client_id"          client-id
                "requested_token"    "openai-api-key"
                "subject_token"      id-token
                "subject_token_type" "urn:ietf:params:oauth:token-type:id_token"}))

;; endregion ^^^^^ Device Code Flow ^^^^^
