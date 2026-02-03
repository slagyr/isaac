(ns mdm.isaac.user.web
  (:require [buddy.core.keys.jwk.rsa]
            [c3kit.apron.schema :as schema]
            [c3kit.bucket.api :as db]
            [c3kit.wire.destination :as destination]
            [c3kit.wire.flash :as flash]
            [c3kit.wire.google :as google]
            [c3kit.wire.jwt :as jwt]
            [c3kit.wire.websocket :as websocket]
            [mdm.isaac.config :as config]
            [mdm.isaac.server.layouts :as layouts]
            [mdm.isaac.server.session :as session]
            [mdm.isaac.user.core :as user]
            [mdm.isaac.user.corec :as userc]
            [ring.util.response :as response]))

(defn authorize-user
  ([user] (authorize-user {} user))
  ([request user] (jwt/update-payload request assoc :user-id (:id user))))

(defn forbid-user [response] (jwt/update-payload response dissoc :user-id))

(defn user-id [req] (-> req :jwt/payload :user-id))

;; region ----- destination -----

(deftype AirworthyDestinationAdapter []
  destination/DestinationAdapter
  (current-user [_ request] (user/current request))
  (default-uri-for-user [_ user]
    (cond (nil? user) "/"
          (:confirmation-token user) "/signup-success"
          :else "/memories"))
  (post-redirect-response [_ destination]
    (layouts/static [:main
                     [:div.floating-panel
                      [:h1.uppercase "Redirect"]
                      [:p.margin-top-default "You are being redirected.  One moment please..."]
                      (destination/post-redirect-form-hiccup destination)]])))

;; endregion ^^^^^ destination ^^^^^

(defn web-signout [request]
  (-> (response/redirect config/host)
      (jwt/copy-payload request)
      forbid-user
      (flash/success "You have been signed out")))

(defn signin-user [response request user]
  (prn "signin-user [response request user]: " [response request user])
  (-> (session/copy response request)
      (destination/add-to-payload user)
      (authorize-user user)))

(defmacro ensure-authenticated [request & body]
  `(let [request# ~request]
     (if (user/current request#)
       (do ~@body)
       (-> (response/redirect "/signin")
           (flash/warn "Please sign in to continue.")
           (assoc-in [:session :destination] (:uri request#))))))

(def verify-account-request-schema
  {:token {:type        :uuid
           :message     "Invalid verification code."
           :validations [{:validate schema/present? :message "Missing verification code."}]}})

(defn web-verify-account [request]
  (let [conformed     (schema/conform verify-account-request-schema (:params request))
        token         (:token conformed)
        user          (delay (db/ffind-by :user :confirmation-token token))
        redirect-home (response/redirect "/")]
    (cond (schema/error? conformed) (flash/error redirect-home (-> conformed schema/message-map :token))
          (nil? @user) (flash/error redirect-home "Unrecognized verification code.")
          :else (let [user (db/tx (dissoc @user :confirmation-token))]
                  (-> (destination/web-redirect request user)
                      (authorize-user user)
                      (flash/success "Your account has been verified!"))))))

(def reset-password-schema
  {:token {:type        :uuid
           :message     "Invalid recovery token."
           :validations [{:validate schema/present? :message "Missing recovery token."}]}})

(defn web-reset-password [request]
  (let [conformed     (schema/conform reset-password-schema (:params request))
        token         (:token conformed)
        user          (delay (db/ffind-by :user :recovery-token token))
        redirect-home (response/redirect "/signin")]
    (cond (schema/error? conformed) (flash/error redirect-home (-> conformed schema/message-map :token))
          (nil? @user) (flash/error redirect-home "Unrecognized recovery token.")
          :else (layouts/web-rich-client request))))

(defn websocket-open [request]
  (println "websocket-open")
  (prn "(user/current request): " (user/current request))
  (when (user/current request)
    (websocket/handler request {:read-csrf jwt/client-id})))

(defn web-success [user request msg]
  (-> (response/redirect (config/link "/memories"))
      (jwt/copy-payload request)
      (authorize-user user)
      (flash/success msg)))

(defn new-social-user! [{:keys [email name]}]
  (db/tx {:kind  :user
          :email email
          :name  name}))

(defn web-authorize-social [request provider user-data]
  (if-let [user (userc/existing-user (:email user-data))]
    (web-success user request "Welcome back!")
    (-> (new-social-user! user-data)
        (web-success request "Welcome to Isaac!"))))

(defn web-error [request msg]
  (->
    (response/redirect (config/link "/"))
    (jwt/copy-payload request)
    (flash/error msg)))

(defn web-google-oauth-login [request]
  (let [client-id   (-> config/active :google-oauth :client-id)
        credentials (-> request :params :credential)]
    (if-let [id-token (try (google/oauth-verification client-id credentials) (catch Exception _))]
      (let [user-data (some-> id-token google/token->payload)]
        (or (when-not (:email-verified? user-data)
              (web-error request "This email address has not been verified by google yet"))
            (web-authorize-social request :google user-data)))
      (web-error request "Invalid google credentials"))))

(defn web-jwt
  "Returns JWT as plain text for valid credentials, or error message with 401 status."
  [request]
  (let [{:keys [email password]} (:params request)
        result                   (user/attempt-signin {:email email :password password})]
    (if (:errors result)
      {:status  401
       :headers {"Content-Type" "text/plain"}
       :body    (or (get-in result [:errors :email]) "invalid credentials")}
      (let [secret   (:jwt-secret config/active)
            lifespan (* 60 60 24 60 1000)  ;; 60 days in ms
            token    (jwt/sign {:user-id (:id result)} secret lifespan)]
        {:status  200
         :headers {"Content-Type" "text/plain"}
         :body    token}))))
