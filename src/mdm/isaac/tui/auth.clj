(ns mdm.isaac.tui.auth
  "Authentication for Isaac terminal client.
   Handles login via HTTP and token management."
  (:require [clojure.edn :as edn]
            [clojure.string :as str])
  (:import [java.net URI HttpURLConnection URL]
           [java.io BufferedReader InputStreamReader OutputStreamWriter]))

(defn- parse-set-cookie
  "Extracts cookie name=value from Set-Cookie header."
  [header]
  (when header
    (first (str/split header #";"))))

(defn- find-cookie
  "Finds a specific cookie from Set-Cookie headers."
  [headers cookie-name]
  (let [cookies (get headers "Set-Cookie")]
    (when cookies
      (some (fn [cookie]
              (when (str/starts-with? cookie (str cookie-name "="))
                (subs cookie (inc (count cookie-name)))))
            (if (string? cookies) [cookies] cookies)))))

(defn- http-post
  "Makes an HTTP POST request with EDN body. Returns {:status :headers :body}."
  [url body]
  (let [conn (doto (.openConnection (URL. url))
               (.setRequestMethod "POST")
               (.setRequestProperty "Content-Type" "application/edn")
               (.setRequestProperty "Accept" "application/edn")
               (.setDoOutput true))]
    (try
      ;; Write body
      (with-open [writer (OutputStreamWriter. (.getOutputStream conn))]
        (.write writer (pr-str body))
        (.flush writer))

      ;; Read response
      (let [status (.getResponseCode conn)
            headers (into {} (for [[k v] (.getHeaderFields conn)
                                   :when k]
                               [k (if (= 1 (count v)) (first v) (vec v))]))
            stream (if (>= status 400)
                     (.getErrorStream conn)
                     (.getInputStream conn))
            body (when stream
                   (with-open [reader (BufferedReader. (InputStreamReader. stream))]
                     (slurp reader)))]
        {:status  status
         :headers headers
         :body    (when body (try (edn/read-string body) (catch Exception _ body)))})
      (finally
        (.disconnect conn)))))

(defn login
  "Attempts to login with email and password.
   Returns {:ok true :token <jwt>} on success, {:ok false :error <msg>} on failure."
  [base-url email password]
  (try
    (let [response (http-post (str base-url "/ajax/user/signin")
                              {:email email :password password})]
      (if (= 200 (:status response))
        (let [token (find-cookie (:headers response) "isaac-token")]
          (if token
            {:ok true :token token}
            {:ok false :error "No token in response"}))
        {:ok false :error (or (get-in response [:body :flash :error])
                              (get-in response [:body :errors :email])
                              "Login failed")}))
    (catch Exception e
      {:ok false :error (.getMessage e)})))

(defn ws-uri->http-base
  "Converts ws://host:port/path to http://host:port"
  [ws-uri]
  (let [uri (URI. ws-uri)]
    (str "http://" (.getHost uri) ":" (.getPort uri))))
