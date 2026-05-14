;; mutation-tested: 2026-05-06
(ns isaac.server.routes
  (:refer-clojure :exclude [error-handler])
  (:require
    [c3kit.apron.util :as util]
    [clojure.string :as str]
    [isaac.config.loader :as config]
    [isaac.system :as system]))

(def ^:dynamic *registry* (atom {}))

(defn fresh-registry [] {})

(defn register-route!
  ([method uri handler]
   (swap! *registry* assoc [method uri] {:handler handler})
   [method uri]))

 #_{:clj-kondo/ignore [:unused-private-var]}
 (defn- register-prefix-route!
  "Register a handler for all requests whose URI begins with uri-prefix."
  ([uri-prefix handler]
   (swap! *registry* assoc [:prefix uri-prefix] {:handler    handler
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

(defn- invoke-route [{:keys [handler]} request]
  (let [handler (resolve-handler handler)]
    (handler request)))

(defn- dispatch-exact-route [table request]
  (some-> (get table [(:request-method request) (:uri request)])
          (invoke-route request)))

(defn- dispatch-prefix-routes [registry request]
  (some (fn [[key route]]
          (when (and (= :prefix (first key))
                     (str/starts-with? (:uri request) (:uri-prefix route)))
            (invoke-route route request)))
        registry))

(defn- dispatch-request [request]
  (or (dispatch-exact-route @*registry* request)
      (dispatch-exact-route built-in-routes request)
      (dispatch-prefix-routes @*registry* request)
      not-found))

(defn handler
  ([request]
   (dispatch-request request))
  ([opts request]
   (let [cfg       (or (when-let [cfg-fn (:cfg-fn opts)] (cfg-fn))
                       (:cfg opts))
         state-dir (:state-dir opts)]
     (if (or cfg state-dir)
       (system/with-nested-system (cond-> {}
                                    state-dir (assoc :state-dir state-dir))
         (when cfg
           (config/set-snapshot! cfg))
         (dispatch-request request))
       (dispatch-request request)))))
