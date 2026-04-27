(ns isaac.server.routes
  (:refer-clojure :exclude [error-handler])
  (:require
    [c3kit.apron.util :as util]))

(def ^:private not-found
  {:status 404 :headers {"Content-Type" "text/plain"} :body "Not found"})

(defn error-handler [_request]
  (throw (ex-info "Intentional error" {:route "/error"})))

(defn- lazy-handle [handler-sym opts request]
  (let [handler @(util/resolve-var handler-sym)]
    (if (= handler-sym 'isaac.server.acp-websocket/handler)
      (handler opts request)
      (handler request))))

(defn- lazy-routes [opts table]
  (fn [request]
    (some (fn [[[method uri] handler-sym]]
            (when (and (= method (:request-method request))
                       (= uri (:uri request)))
              (lazy-handle handler-sym opts request)))
           table)))

(def ^:private route-table
  {[:get "/acp"]    'isaac.server.acp-websocket/handler
   [:get "/status"] 'isaac.server.status/handle
   [:get "/error"]  'isaac.server.routes/error-handler})

(defn handler
  ([request]
   (handler {} request))
  ([opts request]
   (or ((lazy-routes opts route-table) request)
       not-found)))
