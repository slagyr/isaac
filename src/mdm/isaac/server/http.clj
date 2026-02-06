(ns mdm.isaac.server.http
  (:require [mdm.isaac.config :as config]
            [mdm.isaac.server.errors :as errors]
            [mdm.isaac.server.jwt :as jwt]
            [mdm.isaac.server.layouts :as layouts]
            [mdm.isaac.server.session :as session]
            [c3kit.apron.log :as log]
            [c3kit.apron.util :as util]
            [c3kit.apron.verbose :as verbose]
            [c3kit.wire.assets :refer [wrap-asset-fingerprint]]
            [compojure.core :refer [defroutes]]
            [compojure.route :as route]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.flash :refer [wrap-flash]]
            [ring.middleware.head :refer [wrap-head]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [wrap-resource]]))

(defn refreshable [handler-sym]
  (if config/development?
    (fn [request] (@(util/resolve-var handler-sym) request))
    (util/resolve-var handler-sym)))

(defroutes web-handler
  (refreshable 'mdm.isaac.server.routes/handler)
  (route/not-found (layouts/not-found)))

(defn app-handler []
  (if config/development?
    (let [refresh-handler (util/resolve-var 'c3kit.apron.refresh/refresh-handler)]
      (refresh-handler 'mdm.isaac.server.http/web-handler))
    (util/resolve-var 'mdm.isaac.server.http/web-handler)))

(defn root-handler []
  (-> (app-handler)
      verbose/wrap-verbose
      errors/wrap-errors
      wrap-flash
      session/wrap-session
      jwt/wrap-user
      jwt/wrap-jwt
      wrap-keyword-params
      wrap-multipart-params
      wrap-nested-params
      wrap-params
      wrap-cookies
      (wrap-resource "public")
      wrap-asset-fingerprint
      wrap-content-type
      wrap-not-modified
      wrap-head
      ))

(defn start [app]
  (let [host (or (System/getenv "HOST") config/host)
        port (or (some-> "PORT" System/getenv Integer/parseInt) config/port)]
    (log/info (str "Starting HTTP server: http://" host ":" port))
    (let [server (run-server (root-handler) {:ip host :port port})]
      (assoc app :http server))))

(defn stop [app]
  (when-let [stop-server-fn (:http app)]
    (log/info "Stopping HTTP server")
    (stop-server-fn :timeout 1000))
  (dissoc app :http))


