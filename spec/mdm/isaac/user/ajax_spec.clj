(ns mdm.isaac.user.ajax-spec
  (:require [c3kit.apron.legend :as legend]
            [c3kit.bucket.api :as db]
            [c3kit.wire.destination :as destination]
            [mdm.isaac.robots :as robots]
            [mdm.isaac.spec-helper :as helper]
            [mdm.isaac.user.ajax :as sut]
            [mdm.isaac.user.core :as user]
            [mdm.isaac.user.web :as user.web]
            [speclj.core :refer :all]))

(describe "Auth Ajax"

  (with-stubs)
  (helper/stub-email)
  (helper/with-fast-password-hash)
  (robots/with-kinds :user)
  (before (destination/configure! (user.web/->AirworthyDestinationAdapter)))

  (context "signin"

    (it "unauthenticated"
      (let [response (sut/ajax-signin {})]
        (should= :ok (-> response :body :status))
        (should= "is required" (-> response :body :payload :errors :email))
        (should= "is required" (-> response :body :payload :errors :password))))

    (it "authenticated"
      (let [response (sut/ajax-signin {:params {:email (:email @robots/robbie) :password "nursemaid"}})]
        (should= :ok (-> response :body :status))
        (should= "/memories" (-> response :body :payload :destination))
        (should= (legend/present! @robots/robbie) (-> response :body :payload :user))
        (should= (:id @robots/robbie) (-> response :jwt/payload :user-id))))

    (it "with a pending destination"
      (let [request  {:params  {:email (:email @robots/robbie) :password "nursemaid"}
                      :session {:foo "bar" :destination {:method :get :uri "/somewhere"}}}
            response (sut/ajax-signin request)]
        (should= "/somewhere" (-> response :body :payload :destination))
        (should= {:foo "bar"} (:session response))))
    )

  (context "signup"

    (it "empty"
      (let [response (sut/ajax-signup {})]
        (should= :ok (-> response :body :status))
        (should= "is required" (-> response :body :payload :errors :email))
        (should= "is required" (-> response :body :payload :errors :password))
        (should= "is required" (-> response :body :payload :errors :confirm-password))))

    (it "success"
      (let [response (sut/ajax-signup {:params {:email            "marion@gmail.com"
                                                :password         "i-heart-indy"
                                                :confirm-password "i-heart-indy"}})
            marion   (db/ffind-by :user :email "marion@gmail.com")]
        (should= :ok (-> response :body :status))
        (should= "/signup-success" (-> response :body :payload :destination))
        (should= (legend/present! marion) (-> response :body :payload :user))
        (should= (:id marion) (-> response :jwt/payload :user-id))))

    (it "with a pending destination"
      (let [request  {:params  {:email            "marion@gmail.com"
                                :password         "i-heart-indy"
                                :confirm-password "i-heart-indy"}
                      :session {:foo "bar" :destination {:method :get :uri "/somewhere"}}}
            response (sut/ajax-signup request)]
        (should= "/somewhere" (-> response :body :payload :destination))
        (should= {:foo "bar"} (:session response))))
    )

  (context "forgot-password"

    (it "validation error"
      (let [response (sut/ajax-forgot-password {})]
        (should= :ok (-> response :body :status))
        (should= "is required" (-> response :body :payload :errors :email))))

    (it "success"
      (let [response (sut/ajax-forgot-password {:params (select-keys @robots/robbie [:email])})]
        (should= :ok (-> response :body :status))
        (should= "ok" (-> response :body :payload))))

    )

  (context "reset-password"

    (it "validation error"
      (let [response (sut/ajax-reset-password {})]
        (should= :ok (-> response :body :status))
        (should= "is required" (-> response :body :payload :errors :recovery-token))))

    (it "success"
      (let [uuid     (random-uuid)
            _        (db/tx @robots/robbie :recovery-token uuid)
            response (sut/ajax-reset-password {:params {:recovery-token   uuid
                                                        :password         "doctor!!"
                                                        :confirm-password "doctor!!"}})]
        (should= :ok (-> response :body :status))
        (should= (legend/present! @robots/robbie) (-> response :body :payload :user))
        (should (user/verify-password "doctor!!" (:password @robots/robbie)))
        (should-be-nil (:recovery-token @robots/robbie))))

    )

  )
