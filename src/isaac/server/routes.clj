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

(defn register-prefix-route!
  "Register a handler for all requests whose URI begins with uri-prefix."
  ([uri-prefix handler]
   (register-prefix-route! uri-prefix handler {}))
  ([uri-prefix handler {:keys [with-opts?]}]
   (swap! *registry* assoc [:prefix uri-prefix] {:handler    handler
                                                  :with-opts? with-opts?
                                                  :uri-prefix uri-prefix})
   [:prefix uri-prefix]))

(defn route-registered? [method uri]
  (contains? @*registry* [method uri]))

(def ^:private not-found
  {:status 404 :headers {"Content-Type" "text/plain"} :body "Not found"})

(defn error-handler [_request]
  (throw (ex-info "Intentional error" {:route "/error"})))

(def ^:private built-in-routes
  {[:get "/status"] {:handler 'isaac.server.status/handle}
   [:get "/error"]  {:handler 'isaac.server.routes/error-handler}})

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

(defn- dispatch-prefix-routes [opts registry request]
  (some (fn [[key route]]
          (when (and (= :prefix (first key))
                     (str/starts-with? (:uri request) (:uri-prefix route)))
            (invoke-route route opts request)))
        registry))

(defn handler
  ([request]
   (handler {} request))
  ([opts request]
   (or (dispatch-exact-route opts @*registry* request)
       (dispatch-exact-route opts built-in-routes request)
       (dispatch-prefix-routes opts @*registry* request)
       not-found)))
