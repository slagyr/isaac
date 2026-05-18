(ns isaac.server.http
  (:require
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.logger :as log]
    [isaac.server.routes :as routes]))

(defn loopback-host? [host]
  (boolean
    (when host
      (or (= "localhost" host)
          (= "::1" host)
          (= "0:0:0:0:0:0:0:1" host)
          (str/starts-with? host "127.")))))

(defn- bearer-token [request]
  (some-> (or (get-in request [:headers "authorization"])
              (get-in request [:headers :authorization]))
          (str/replace-first #"(?i)^Bearer\s+" "")))

(defn wrap-auth [opts handler]
  (fn [request]
    (let [cfg   (or (when-let [cfg-fn (:cfg-fn opts)] (cfg-fn))
                     (:cfg opts)
                     {})
          ;; Prefer the actual bind host (recorded at start! time) over the
          ;; one resolved from config — those defaults can disagree.
          host  (or (:bind-host opts)
                    (:host (config/server-config cfg)))
          token (get-in cfg [:server :auth :token])]
      (if (or (loopback-host? host)
              (str/blank? token)
              (= token (bearer-token request)))
        (handler request)
        {:status 401
         :headers {"Content-Type" "text/plain"
                   "WWW-Authenticate" "Bearer"}
         :body "Unauthorized"}))))

(defn wrap-logging [handler]
  (fn [request]
    (let [method (:request-method request)
          uri    (:uri request)
          start  (System/currentTimeMillis)]
      (log/debug :server/request-received :method method :uri uri)
      (try
        (let [response (handler request)
              ms       (- (System/currentTimeMillis) start)]
          (log/debug :server/response-sent :method method :uri uri :status (:status response) :ms ms)
          response)
        (catch Exception e
          (let [ms (- (System/currentTimeMillis) start)]
           (log/ex :server/request-failed e {:method method
                                              :uri    uri
                                              :status 500
                                              :ms     ms}))
          {:status 500 :headers {"Content-Type" "text/plain"} :body "Internal Server Error"})))))                       

(defn root-handler [request]
  (routes/handler request))

(defn create-handler
  ([]
   (create-handler {}))
  ([opts-or-handler]
   (if (fn? opts-or-handler)
     (wrap-logging opts-or-handler)
      (wrap-logging
        (wrap-auth opts-or-handler
                   (fn [request]
                     (routes/handler opts-or-handler request)))))))
