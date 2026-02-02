(ns mdm.isaac.server.routes
  (:require
    [c3kit.apron.corec :as ccc]
    [c3kit.apron.util :as util]
    [c3kit.wire.ajax :as ajax]
    [c3kit.wire.jwt :as jwt]
    [c3kit.wire.routes :as wire.routes]
    [compojure.core :as compojure]
    [mdm.isaac.config :as config]
    [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]))

(def ws-handlers
  {:user/fetch-data  'mdm.isaac.user.ws/ws-fetch-user-data
   :goals/list       'mdm.isaac.goal.ws/ws-list
   :goals/add        'mdm.isaac.goal.ws/ws-add
   :goals/update     'mdm.isaac.goal.ws/ws-update
   :thoughts/recent  'mdm.isaac.thought.ws/ws-recent
   :thoughts/search  'mdm.isaac.thought.ws/ws-search
   :shares/unread    'mdm.isaac.share.ws/ws-unread
   :shares/ack       'mdm.isaac.share.ws/ws-ack})

(defn valid-google-credentials? [{:keys [params uri cookies]}]
  (and (= "/signin/google-oauth" uri)
       (= (get-in cookies ["g_csrf_token" :value])
          (:g_csrf_token params))))

(defn valid-apple-credentials? [{:keys [uri]}]
  (= "/signin/apple-oauth" uri))

(defn wrap-custom-anti-forgery [handler]
  (let [default-handler (wrap-anti-forgery handler {:strategy (jwt/create-strategy)})]
    (fn [request]
      (if (or (not= :post (:request-method request))
              (get-in request [:headers "authorization"])
              (valid-google-credentials? request)
              (valid-apple-credentials? request))
        (handler request)
        (default-handler request)))))

(defn ajax-not-found [request]
  (ajax/fail (:uri request) (str "AJAX route not found: " (:uri request))))

(def ajax-routes-handler
  (-> (wire.routes/lazy-routes
        {
         ["/user/signin" :post]          mdm.isaac.user.ajax/ajax-signin
         ["/user/signup" :post]          mdm.isaac.user.ajax/ajax-signup
         ["/user/forgot-password" :post] mdm.isaac.user.ajax/ajax-forgot-password
         ["/user/reset-password" :post]  mdm.isaac.user.ajax/ajax-reset-password
         })
      (wire.routes/wrap-prefix "/ajax" ajax-not-found)
      ajax/wrap-ajax))

(def web-routes-handlers
  (-> (wire.routes/lazy-routes
        {
         ["/" :get]                      mdm.isaac.server.layouts/web-rich-client
         ["/account/verify/:token" :get] mdm.isaac.user.web/web-verify-account
         ["/error" :any]                 mdm.isaac.server.errors/web-error
         ["/recover/:token" :get]        mdm.isaac.user.web/web-reset-password
         ["/redirect" :any]              c3kit.wire.destination/web-redirect
         ["/signin" :get]                mdm.isaac.server.layouts/web-rich-client
         ["/signout" :any]               mdm.isaac.user.web/web-signout
         ["/signout/:reason" :any]       mdm.isaac.user.web/web-signout
         ["/signup" :get]                mdm.isaac.server.layouts/web-rich-client
         ["/signup-success" :any]        mdm.isaac.server.layouts/web-rich-client
         ["/terms" :any]                 mdm.isaac.server.layouts/web-rich-client
         ["/signin/google-oauth" :post]  mdm.isaac.user.web/web-google-oauth-login
         ["/user/ws" :any]               mdm.isaac.user.web/websocket-open
         })
      wrap-custom-anti-forgery))

(defn create-dev-handler []
  (wire.routes/lazy-routes
    {
     ["/sandbox" :get]                 mdm.isaac.sandbox.core/index
     ["/sandbox/" :get]                mdm.isaac.sandbox.core/index
     ["/sandbox/:page" :get]           mdm.isaac.sandbox.core/handler
     ["/sandbox/:page/:ns" :get]       mdm.isaac.sandbox.core/handler
     ["/sandbox/:page/:ns1/:ns2" :get] mdm.isaac.sandbox.core/handler
     }))

(compojure/defroutes handler
  ajax-routes-handler
  web-routes-handlers
  (if (config/production?) ccc/noop (create-dev-handler))
  )


