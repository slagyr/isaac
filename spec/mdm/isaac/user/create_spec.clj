(ns mdm.isaac.user.create-spec
  (:require [c3kit.bucket.api :as db]
            [mdm.isaac.robots :as robots]
            [mdm.isaac.spec-helper :as helper]
            [mdm.isaac.user.core :as user]
            [mdm.isaac.user.create :as sut]
            [speclj.core :refer :all]))

(describe "User Create"

  (with-stubs)
  (helper/with-fast-password-hash)
  (robots/with-kinds :user)

  (context "create-user!"

    (it "creates a user with hashed password"
      (let [result (sut/create-user! "newuser@example.com" "secretpass")]
        (should= :ok (:status result))
        (should-not-be-nil (:user result))
        (let [user (db/ffind-by :user :email "newuser@example.com")]
          (should-not-be-nil user)
          (should= "newuser@example.com" (:email user))
          (should (user/verify-password "secretpass" (:password user))))))

    (it "lowercases email"
      (let [result (sut/create-user! "UPPER@CASE.COM" "password123")]
        (should= :ok (:status result))
        (let [user (db/ffind-by :user :email "upper@case.com")]
          (should-not-be-nil user))))

    (it "returns error for invalid email format"
      (let [result (sut/create-user! "not-an-email" "password123")]
        (should= :error (:status result))
        (should-contain "email" (:message result))))

    (it "returns error for duplicate email"
      (let [result (sut/create-user! "robbie@isaac.com" "password123")]
        (should= :error (:status result))
        (should-contain "already" (:message result))))

    (it "returns error for short password"
      (let [result (sut/create-user! "valid@email.com" "short")]
        (should= :error (:status result))
        (should-contain "8" (:message result)))))

  (context "valid-email?"

    (it "accepts valid emails"
      (should= true (sut/valid-email? "user@example.com"))
      (should= true (sut/valid-email? "user.name@example.co.uk")))

    (it "rejects invalid emails"
      (should= false (sut/valid-email? "not-an-email"))
      (should= false (sut/valid-email? "@example.com"))
      (should= false (sut/valid-email? "user@"))
      (should= false (sut/valid-email? ""))))

  (context "valid-password?"

    (it "accepts passwords with 8+ characters"
      (should= true (sut/valid-password? "12345678"))
      (should= true (sut/valid-password? "longerpassword")))

    (it "rejects passwords with less than 8 characters"
      (should= false (sut/valid-password? "1234567"))
      (should= false (sut/valid-password? "short"))
      (should= false (sut/valid-password? "")))))
