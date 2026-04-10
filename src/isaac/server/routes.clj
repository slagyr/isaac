(ns isaac.server.routes
  (:require
    [c3kit.apron.util :as util]))

(def ^:private not-found
  {:status 404 :headers {"Content-Type" "text/plain"} :body "Not found"})

(defn error-handler [_request]
  (throw (ex-info "Intentional error" {:route "/error"})))

(defn- lazy-handle [handler-sym request]
  (let [handler @(util/resolve-var handler-sym)]
    (handler request)))

(defn- lazy-routes [table]
  (fn [request]
    (some (fn [[[method uri] handler-sym]]
            (when (and (= method (:request-method request))
                       (= uri (:uri request)))
              (lazy-handle handler-sym request)))
          table)))

(def ^:private route-handler
  (lazy-routes {[:get "/status"] 'isaac.server.status/handle
                [:get "/error"]  'isaac.server.routes/error-handler}))

(defn handler [request]
  (or (route-handler request)
      not-found))
