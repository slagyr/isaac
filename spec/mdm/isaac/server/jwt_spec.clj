(ns mdm.isaac.server.jwt-spec

  (:require [mdm.isaac.config :as config]
            [mdm.isaac.server.jwt :as sut]
            [c3kit.apron.time :as time]
            [c3kit.wire.jwt :as jwt]
            [compojure.core :refer [GET defroutes]]
            [speclj.core :refer :all]))

(defroutes routes
  (GET "/blah" [] {:status 200 :body "blah"}))

(def jwt-secret (:jwt-secret config/active))

(declare handler)

(def test-jwt-config {:cookie-name         "rewind-token"
                      :secret              jwt-secret
                      :lifespan            (when config/development? (time/hours 3))
                      :refresh-cookie-name "rewind-refresh-token"
                      :refresh-lifespan    (time/hours 336)})

(describe "JWT wrapper"

  (redefs-around [sut/config test-jwt-config])
  (with handler (sut/wrap-jwt routes))

  (it "invalid signature"
    (let [token   (jwt/sign {:user-id 123} "blah" (time/hours 1))
          cookies {"rewind-token" {:value token}}
          resp    (@handler {:uri "/blah" :request-method :get :cookies cookies})]
      (should-not= 123 (:user-id (:jwt/payload resp)))))

  (it "set in cookie"
    (let [token   (jwt/sign {:user-id 123} jwt-secret (time/hours 1))
          cookies {"rewind-token" {:value token}}
          resp    (@handler {:uri "/blah" :request-method :get :cookies cookies})]
      (should= 123 (:user-id (:jwt/payload resp)))))

  (it "set in authorization header"
    (let [token   (jwt/sign {:user-id 123} jwt-secret (time/hours 1))
          headers {"authorization" (str "Bearer " token)}
          resp    (@handler {:uri "/blah" :request-method :get :headers headers})]
      (should= 123 (:user-id (:jwt/payload resp)))))

  (it "set refresh token in x-refresh- header"
    (let [token   (jwt/sign {:user-id 123} jwt-secret (time/hours 1))
          headers {"x-refresh-token" (str "Bearer " token)}
          resp    (@handler {:uri "/blah" :request-method :get :headers headers})]
      (should= 123 (:user-id (:jwt/payload resp)))))

  (it "sets refresh cookie"
    (let [{:keys [cookies]} (@handler {:uri "/blah" :request-method :get})
          cookie-value (:value (get cookies "rewind-refresh-token"))
          decoded      (jwt/unsign cookie-value jwt-secret)]
      (should-not-be-nil cookie-value)
      (should= (+ (:iat decoded) (time/millis->seconds (:refresh-lifespan test-jwt-config))) (:exp decoded))))

  (it "refresh cookie generates a new access token and cookie if expired"
    (let [token              (jwt/sign {:user-id 123} jwt-secret 0)
          refresh-token      (jwt/sign {:user-id 123} jwt-secret (time/hours 2))
          cookies            {"rewind-token" {:value token} "rewind-refresh-token" {:value refresh-token}}
          resp               (@handler {:uri "/blah" :request-method :get :cookies cookies})
          new-cookies        (:cookies resp)
          new-token          (:value (get new-cookies "rewind-token"))
          new-access-decoded (jwt/unsign new-token jwt-secret)]
      (should= 123 (:user-id new-access-decoded))
      (should= 123 (:user-id (:jwt/payload resp)))))

  (it "does not generate new access token if refresh token is expired too"
    (let [token              (jwt/sign {:user-id 123} jwt-secret 0)
          refresh-token      (jwt/sign {:user-id 123} jwt-secret 0)
          cookies            {"rewind-token" {:value token} "rewind-refresh-token" {:value refresh-token}}
          resp               (@handler {:uri "/blah" :request-method :get :cookies cookies})
          new-cookies        (:cookies resp)
          new-token          (:value (get new-cookies "rewind-token"))
          new-access-decoded (jwt/unsign new-token jwt-secret)]
      (should-be-nil (:user-id new-access-decoded))
      (should-be-nil (:user-id (:jwt/payload resp)))
      (should-not= new-access-decoded (jwt/unsign token jwt-secret))))
  )
