;; mutation-tested: 2026-05-06
(ns isaac.server.routes
  (:refer-clojure :exclude [error-handler])
  (:require
    [c3kit.apron.util :as util]
    [clojure.string :as str]))

(def ^:dynamic *registry* (atom {}))

(defn fresh-registry [] {})

(defn register-route!
  ([method uri handler]
   (register-route! method uri handler {}))
  ([method uri handler {:keys [with-opts?]}]
   (swap! *registry* assoc [method uri] {:handler    handler
                                         :with-opts? with-opts?})
   [method uri]))

(defn route-registered? [method uri]
  (contains? @*registry* [method uri]))

(def ^:private not-found
  {:status 404 :headers {"Content-Type" "text/plain"} :body "Not found"})

(defn error-handler [_request]
  (throw (ex-info "Intentional error" {:route "/error"})))

(def ^:private built-in-routes
  {[:get "/status"] {:handler 'isaac.server.status/handle}
   [:get "/error"]  {:handler 'isaac.server.routes/error-handler}})

(def ^:private built-in-prefix-routes
  ;; Core prefix routes stay built-in until the registry grows wildcard matching.
  [{:handler     'isaac.server.hooks/handler
    :uri-prefix  "/hooks/"
    :with-opts?  true}])

(defn- resolve-handler [handler-ref]
  (cond
    (symbol? handler-ref) @(util/resolve-var handler-ref)
    (var? handler-ref)    @handler-ref
    :else                 handler-ref))

(defn- invoke-route [{:keys [handler with-opts?]} opts request]
  (let [handler (resolve-handler handler)]
    (if with-opts?
      (handler opts request)
      (handler request))))

(defn- dispatch-exact-route [opts table request]
  (some-> (get table [(:request-method request) (:uri request)])
          (invoke-route opts request)))

(defn- dispatch-prefix-routes [opts routes request]
  (some (fn [{:keys [uri-prefix] :as route}]
          (when (str/starts-with? (:uri request) uri-prefix)
            (invoke-route route opts request)))
        routes))

(defn handler
  ([request]
   (handler {} request))
  ([opts request]
   (or (dispatch-exact-route opts @*registry* request)
       (dispatch-exact-route opts built-in-routes request)
       (dispatch-prefix-routes opts built-in-prefix-routes request)
       not-found)))
