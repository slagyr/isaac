(ns mdm.isaac.user.core-spec
  (:require [mdm.isaac.config :as config]
            [mdm.isaac.robots :as robots]
            [mdm.isaac.spec-helper :as helper]
            [mdm.isaac.user.core :as sut]
            [c3kit.apron.log :as log]
            [c3kit.apron.schema :as schema]
            [c3kit.bucket.api :as db]
            [clojure.string :as str]
            [speclj.core :refer :all]))

(def uuid (random-uuid))
(declare entity)

(describe "User Core hashing"

  (tags :slow)

  (it "hashes the password"
    (let [hash1 (sut/hash-password "hello there")
          hash2 (sut/hash-password "hello there")]
      (should-not= "hello there" hash1)
      (should-not= "hello there" hash2)
      (should-not= hash1 hash2)))

  (it "verifies a hashed password"
    (let [hash1 (sut/hash-password "hello there")
          hash2 (sut/hash-password "hello there")]
      (should= true (sut/verify-password "hello there" hash1))
      (should= true (sut/verify-password "hello there" hash2))
      (should= false (sut/verify-password "wrong" hash1))
      (should= false (sut/verify-password "wrong" hash2))
      (should= false (sut/verify-password nil hash2))
      (should= false (sut/verify-password "wrong" nil))
      (should= false (sut/verify-password nil nil))))

  )

(describe "User Core"

  (with-stubs)
  (helper/with-fast-password-hash)
  (helper/stub-email)
  (robots/with-kinds :user)

  (context "request"

    (it "id"
      (should= nil (sut/id {}))
      (should= "abc123" (sut/id {:jwt/payload {:user-id "abc123"}})))

    (it "current"
      (let [request {:jwt/payload {:user-id (:id @robots/robbie)}}]
        (should= @robots/robbie (sut/current request)))
      (let [request {:user robots/robbie}]
        (should= @robots/robbie (sut/current request))))

    )

  (context "attempt-signin"

    (it "nil"
      (should= nil (sut/authorized-user {}))
      (should= nil (sut/authorized-user {:email (:email @robots/robbie)}))
      (should= nil (sut/authorized-user {:password (sut/hash-password (:password @robots/robbie))}))
      (should= nil (sut/authorized-user {:password (:password @robots/robbie)})))

    (it "confirmation token makes users unauthorized"
      (db/tx @robots/robbie :confirmation-token (random-uuid))
      (should= nil (sut/authorized-user {:email (:email @robots/robbie) :password "nursemaid"})))

    (it "success"
      (should= @robots/robbie (sut/authorized-user {:email (:email @robots/robbie) :password "nursemaid"}))
      (should= @robots/speedy (sut/authorized-user {:email (:email @robots/speedy) :password "runaround"}))
      (should= @robots/robbie (sut/authorized-user {:email (str/upper-case (:email @robots/robbie)) :password "nursemaid"})))

    (it "email is case-insensitive"
      (should= @robots/robbie (sut/authorized-user {:email (str/upper-case (:email @robots/robbie)) :password "nursemaid"})))

    )

  (context "signin-schema"

    (it "email is lower case"
      (let [credentials (schema/conform sut/signin-schema {:email "JOE@ACME.COM" :password "foo"})]
        (should= "joe@acme.com" (:email credentials))))

    )

  (context "authenticate-credentials"

    (it "empty"
      (let [result (sut/attempt-signin {})]
        (should= {:errors {:email "is required" :password "is required"}} result)))

    (it "email hasn't been confirmed"
      (db/tx @robots/robbie :confirmation-token (random-uuid))
      (let [result (sut/attempt-signin {:email (:email @robots/robbie) :password "nursemaid"})]
        (should= {:errors {:email "check your email for verification link"}} result)))

    (it "missing email"
      (let [result (sut/attempt-signin {:email "joe@acme.com" :password "blah"})]
        (should= {:errors {:email "invalid credentials"}} result)))

    (it "invalid password"
      (let [result (sut/attempt-signin {:email (:email @robots/robbie) :password "wrong"})]
        (should= {:errors {:email "invalid credentials"}} result)))

    (it "success"
      (should= @robots/robbie (sut/attempt-signin {:email (:email @robots/robbie) :password "nursemaid"}))
      (should= @robots/speedy (sut/attempt-signin {:email (:email @robots/speedy) :password "runaround"})))

    (it "email is case-insensitive"
      (let [result (sut/attempt-signin {:email (str/upper-case (:email @robots/robbie)) :password "nursemaid"})]
        (should= @robots/robbie result)))

    )

  (context "attempt-signup"

    (redefs-around [random-uuid (stub :new-uuid {:return uuid})])

    (context "fails when"

      (it "no email"
        (let [result (sut/attempt-signup {})]
          (should= "is required" (-> result :errors :email))))

      (it "email already in use"
        (let [result (sut/attempt-signup {:email            "robbie@isaac.com"
                                          :password         "password"
                                          :confirm-password "password"})]
          (should= "already in use" (-> result :errors :email))))

      )

    (it "success"
      (let [result (sut/attempt-signup {:email            "Marion@gmail.com"
                                        :password         "I love Indiana"
                                        :confirm-password "I love Indiana"})
            marion (db/ffind-by :user :email "marion@gmail.com")]
        (should-not-be-nil marion)
        (should= uuid (:confirmation-token marion))
        (should (sut/verify-password "I love Indiana" (:password marion)))
        (should= marion result)))

    (context "confirmation email"

      (it "structure"
        (with-redefs [config/admin-email "Indy <indy@marshall.edu>"]
          (let [email (sut/confirmation-email "rbelloq@villainy.com" "https://confirm.link")]
            (should= "rbelloq@villainy.com" (:to email))
            (should= "Indy <indy@marshall.edu>" (:from email))
            (should= "Airworthy: Confirm your new account" (:subject email))
            (should= (str/join "\n"
                               ["Click this link to begin using your new Airworthy account:\n"
                                "https://confirm.link"])
                     (:text email)))))

      (it "is sent"
        (sut/attempt-signup {:email            "marion@gmail.com"
                             :password         "I love Indiana"
                             :confirm-password "I love Indiana"})
        (let [expected (sut/confirmation-email "marion@gmail.com" (config/link "/account/verify/" uuid))]
          (should-have-invoked :email/send! {:with [expected]})))
      )
    )

  (context "attempt-forgot-password"

    (redefs-around [random-uuid (constantly uuid)])

    (it "email missing"
      (let [result (sut/attempt-forgot-password {})]
        (should= "is required" (-> result :errors :email))))

    (it "bad email"
      (let [result (sut/attempt-forgot-password {:email "blah"})]
        (should= "must be a valid email" (-> result :errors :email))))

    (it "missing user"
      (log/capture-logs
        (let [result (sut/attempt-forgot-password {:email "foo@blah.com"})]
          (should-not-have-invoked :email/send!)
          (should= {} result))
        (should-contain "attempted account recovery" (log/captured-logs-str))))

    (it "recovery email structure"
      (let [email (sut/recovery-email (:email @robots/robbie) "PERMALINK")]
        (should= (:email @robots/robbie) (:to email))
        (should= config/admin-email (:from email))
        (should= "Airworthy: Recover your account" (:subject email))
        (should-contain (config/link "/recover/PERMALINK") (:text email))))

    (it "success"
      (let [result (sut/attempt-forgot-password (select-keys @robots/robbie [:email]))]
        (should= {} result)
        (should-have-invoked :email/send!)
        (should= uuid (:recovery-token @robots/robbie))))
    )

  (context "api-reset-password"

    (redefs-around [random-uuid (constantly uuid)])
    (with entity {:recovery-token   uuid
                  :password         "yes snakes"
                  :confirm-password "yes snakes"})
    (before (db/tx @robots/robbie :recovery-token uuid))

    (it "invalid"
      (let [result (sut/attempt-password-reset {})]
        (should= "is required" (-> result :errors :recovery-token))))

    (it "no user with recovery token"
      (db/tx (dissoc @robots/robbie :recovery-token))
      (let [result (sut/attempt-password-reset @entity)]
        (should= "is missing, expired, or used" (-> result :errors :recovery-token))))

    (it "success"
      (let [result (sut/attempt-password-reset @entity)]
        (should (sut/verify-password "yes snakes" (:password @robots/robbie)))
        (should-be-nil (:recovery-token @robots/robbie))
        (should= @robots/robbie result)))
    )

  )
