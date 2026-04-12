(ns isaac.server.http
  (:require
    [isaac.logger :as log]
    [isaac.server.routes :as routes]))

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
     (wrap-logging (fn [request]
                     (routes/handler opts-or-handler request))))))
