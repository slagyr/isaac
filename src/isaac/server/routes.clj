(ns isaac.server.routes
  (:require [isaac.server.status :as status]))

(def ^:private not-found
  {:status 404 :headers {"Content-Type" "text/plain"} :body "Not found"})

(defn handler [request]
  (case [(:request-method request) (:uri request)]
    [:get "/status"] (status/handle request)
    [:get "/error"]  (throw (ex-info "Intentional error" {:route "/error"}))
    not-found))
