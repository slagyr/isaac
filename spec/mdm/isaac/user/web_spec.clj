(ns mdm.isaac.user.web-spec
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.wire.destination :as destination]
            [c3kit.wire.flash :as flash]
            [c3kit.wire.jwt :as jwt]
            [c3kit.wire.google :as google]
            [c3kit.wire.websocket :as websocket]
            [clojure.string :as str]
            [mdm.isaac.config :as config]
            [mdm.isaac.robots :as robots]
            [mdm.isaac.server.layouts :as layouts]
            [mdm.isaac.spec-helper :as helper]
            [mdm.isaac.user.core :as user]
            [mdm.isaac.user.web :as user.web]
            [mdm.isaac.user.web :as sut]
            [c3kit.bucket.api :as db]
            [c3kit.wire.spec-helper :as wire]
            [speclj.core :refer :all])
  (:import (com.google.api.client.googleapis.auth.oauth2 GoogleIdToken$Payload)
           (com.google.api.client.json.webtoken JsonWebToken JsonWebToken$Header)))

(declare request)
(def adapter (sut/->AirworthyDestinationAdapter))
(def uuid (ccc/new-uuid))

(describe "User Web"

  (robots/with-kinds :user)
  (with-stubs)

  (context "destination adapter"

    (it "default-uri-for-user"
      (should= "/" (destination/default-uri-for-user adapter nil))
      (should= "/memories" (destination/default-uri-for-user adapter @robots/robbie))
      (let [user (assoc @robots/robbie :confirmation-token (random-uuid))]
        (should= "/signup-success" (destination/default-uri-for-user adapter user))))

    (it "post-redirect-response"
      (let [response (destination/post-redirect-response adapter {:uri "/somewhere"})]
        (should-contain "<form" (:body response))
        (should-contain "floating-panel" (:body response))
        (should-contain "You are being redirected.  One moment please..." (:body response))))

    )

  (context "web-signout"

    (it "signs out"
      (let [response (sut/web-signout (user.web/authorize-user {} @robots/robbie))]
        (wire/should-redirect-to response config/host)
        (should= "You have been signed out" (flash/first-msg-text response))
        (should= :success (flash/first-msg-level response))
        (should-be-nil (-> response :jwt/payload :user-id))))
    )

  (context "ensure-authenticated"

    (robots/with-kinds :user)

    (it "runs the body when authenticated"
      (let [request (sut/authorize-user @robots/robbie)]
        (should= :foo (sut/ensure-authenticated request :foo))))

    (context "unauthenticated"

      (it "redirects to signin"
        (wire/should-redirect-to (sut/ensure-authenticated {} :foo) "/signin"))

      (it "includes friendly flash"
        (let [response (sut/ensure-authenticated {} :foo)]
          (helper/should-flash-warn "Please sign in to continue." response)))

      (it "saves destination"
        (let [response (sut/ensure-authenticated {:uri "/the/place/to/be"} :foo)]
          (should= "/the/place/to/be" (-> response :session :destination))))
      )

    )

  (context "web-verify-account"

    (it "no token"
      (let [response (sut/web-verify-account {})]
        (wire/should-redirect-to response "/")
        (should-be-nil (:jwt/payload response))
        (helper/should-flash-error "Missing verification code." response)))

    (it "bad token"
      (let [response (sut/web-verify-account {:params {:token "blah"}})]
        (wire/should-redirect-to response "/")
        (should-be-nil (:jwt/payload response))
        (helper/should-flash-error "Invalid verification code." response)))

    (it "unrecognized token"
      (let [response (sut/web-verify-account {:params {:token (str (random-uuid))}})]
        (wire/should-redirect-to response "/")
        (should-be-nil (:jwt/payload response))
        (helper/should-flash-error "Unrecognized verification code." response)))

    (it "success"
      (let [token (random-uuid)]
        (db/tx @robots/robbie :confirmation-token token)
        (let [response (sut/web-verify-account {:params {:token token}})]
          (wire/should-redirect-to response "/memories")
          (should-not-be-nil (:jwt/payload response))
          (helper/should-flash-success "Your account has been verified!" response))
        (should-be-nil (:confirmation-token @robots/robbie))))

    (it "success - saved destination"
      (db/tx @robots/robbie :confirmation-token (random-uuid))
      (let [request  (-> {:params {:token (:confirmation-token @robots/robbie)}}
                         (destination/save-in-session {:method :get :uri "/somewhere"}))
            response (sut/web-verify-account request)]
        (wire/should-redirect-to response "/somewhere")))
    )

  (context "web-reset-password"

    (it "no token"
      (let [response (sut/web-reset-password {})]
        (wire/should-redirect-to response "/signin")
        (helper/should-flash-error "Missing recovery token." response)))

    (it "bad token"
      (let [response (sut/web-reset-password {:params {:token "blah"}})]
        (wire/should-redirect-to response "/signin")
        (helper/should-flash-error "Invalid recovery token." response)))

    (it "unrecognized token"
      (let [response (sut/web-reset-password {:params {:token (str (random-uuid))}})]
        (wire/should-redirect-to response "/signin")
        (helper/should-flash-error "Unrecognized recovery token." response)))

    (it "success"
      (let [token (random-uuid)]
        (db/tx @robots/robbie :recovery-token token)
        (let [request  {:params {:token token}}
              response (sut/web-reset-password request)]
          (should= (layouts/web-rich-client request) response))))
    )

  (context "websocket-open"

    (redefs-around [websocket/handler (stub :websocket/handler {:return :connected})])

    (it "no user"
      (should-be-nil (sut/websocket-open {}))
      (should-not-have-invoked :websocket/handler))

    (it "with user"
      (let [request  (user.web/authorize-user @robots/robbie)
            response (sut/websocket-open request)]
        (should= :connected response)
        (should-have-invoked :websocket/handler {:with [request {:read-csrf jwt/client-id}]})))
    )

  (context "web-google-oauth-login"

    (redefs-around [ccc/new-uuid (stub :new-uuid {:return uuid})])

    (it "failed token verification"
      (with-redefs [google/oauth-verification (stub :verify {:return nil})]
        (let [response (sut/web-google-oauth-login {:params {:credential "abc"}})]
          (wire/should-redirect-to response "/")
          (should= "Invalid google credentials" (-> response :flash :messages first :text))
          (should-have-invoked :verify {:with [(-> config/active :google-oauth :client-id) "abc"]}))))

    (it "unverified email"
      (let [payload  (doto (new GoogleIdToken$Payload)
                       (.setEmail "john@beatles.com")
                       (.setEmailVerified false))
            id-token (new JsonWebToken (new JsonWebToken$Header) payload)]
        (with-redefs [google/oauth-verification (stub :verify {:return id-token})]
          (let [response (sut/web-google-oauth-login {:params {:credential "abc"}})]
            (wire/should-redirect-to response "/")
            (should= "This email address has not been verified by google yet" (-> response :flash :messages first :text))))))

    (it "success - existing user"
      (let [payload  (doto (new GoogleIdToken$Payload)
                       (.setEmail (:email @robots/robbie))
                       (.setEmailVerified true))
            id-token (new JsonWebToken (new JsonWebToken$Header) payload)]
        (with-redefs [google/oauth-verification (stub :verify {:return id-token})]
          (let [response (sut/web-google-oauth-login {:params {:credential "abc"}})]
            (should= "Welcome back!" (-> response :flash :messages first :text))
            (wire/should-redirect-to response "/memories")
            (should= (db/reload @robots/robbie) (user/current response))))))

    (it "signup success"
      (let [payload  (doto (new GoogleIdToken$Payload)
                       (.setEmail "cody@beatles.com")
                       (.setEmailVerified true)
                       (.set "name" "Cody Clean"))
            id-token (new JsonWebToken (new JsonWebToken$Header) payload)]
        (with-redefs [google/oauth-verification (stub :verify {:return id-token})]
          (let [response (sut/web-google-oauth-login {:params {:credential "abc"}})
                cody     (db/ffind-by :user :email "cody@beatles.com")]
            (should= "Welcome to Isaac!" (-> response :flash :messages first :text))
            (should-not-be-nil cody)
            (should= "Cody Clean" (:name cody))
            (should-be-nil (:password cody))
            (should-be-nil (:social-id cody))))))
    )

  (context "web-jwt"

    (it "missing credentials"
      (let [response (sut/web-jwt {:params {}})]
        (should= 401 (:status response))
        (should= "text/plain" (get-in response [:headers "Content-Type"]))))

    (it "invalid email"
      (let [response (sut/web-jwt {:params {:email "bad" :password "password"}})]
        (should= 401 (:status response))
        (should-contain "email" (:body response))))

    (it "wrong password"
      (let [response (sut/web-jwt {:params {:email (:email @robots/robbie) :password "wrong"}})]
        (should= 401 (:status response))
        (should-contain "invalid" (:body response))))

    (it "unconfirmed user"
      (db/tx @robots/robbie :confirmation-token (random-uuid))
      (let [response (sut/web-jwt {:params {:email (:email @robots/robbie) :password "nursemaid"}})]
        (should= 401 (:status response))
        (should-contain "verification" (:body response))))

    (it "success"
      (let [response (sut/web-jwt {:params {:email (:email @robots/robbie) :password "nursemaid"}})]
        (should= 200 (:status response))
        (should= "text/plain" (get-in response [:headers "Content-Type"]))
        (let [token   (:body response)
              decoded (jwt/unsign token (:jwt-secret config/active))]
          (should-not-be-nil token)
          (should= 3 (count (str/split token #"\.")))  ;; JWT has 3 parts
          (should= (:id @robots/robbie) (:user-id decoded))
          (should-not-be-nil (:client-id decoded)))))
    )

  )


