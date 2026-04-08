(ns isaac.server.http
  (:require
    [isaac.logger :as log]
    [isaac.server.routes :as routes]))

(defn wrap-logging [handler]
  (fn [request]
    (let [method (:request-method request)
          uri    (:uri request)
          start  (System/currentTimeMillis)]
      (log/info {:event :http/request-start :method method :uri uri})
      (try
        (let [response (handler request)
              ms       (- (System/currentTimeMillis) start)]
          (log/info {:event :http/request-finish :method method :uri uri
                     :status (:status response) :ms ms})
          response)
        (catch Exception e
          (let [ms (- (System/currentTimeMillis) start)]
            (log/error {:event :http/request-error :method method :uri uri
                        :error (.getMessage e) :ms ms}))
          (throw e))))))

(defn create-handler
  ([]
   (create-handler routes/handler))
  ([inner-handler]
   (wrap-logging inner-handler)))
