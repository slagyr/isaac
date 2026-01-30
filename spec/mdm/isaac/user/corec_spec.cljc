(ns mdm.isaac.user.corec-spec

  (:require [speclj.core #?(:clj :refer :cljs :refer-macros) [after after-all around around-all before before before-all
                                                              context describe focus-context focus-describe focus-it it
                                                              pending should should-be should-be-a should-be-nil
                                                              should-be-same should-contain should-end-with should-fail
                                                              should-have-invoked should-invoke should-not should-not
                                                              should-not-be should-not-be-a should-not-be-nil
                                                              should-not-be-same should-not-contain should-not-end-with
                                                              should-not-have-invoked should-not-invoke
                                                              should-not-start-with should-not-throw should-not=
                                                              should-not== should-start-with should-throw should<
                                                              should<= should= should== should> should>= stub tags
                                                              with with-all with-stubs xit]]
            [mdm.isaac.user.corec :as sut]
            [c3kit.apron.schema :as schema]))

(declare entity)

(describe "User Core Common"

  (context "signin schema"

    (with entity {:password "password" :email "blah@blah.com"})

    (it "required"
      (let [result (schema/conform-message-map sut/signin-schema {})]
        (should= "is required" (:email result))
        (should= "is required" (:password result))))

    (it "email format"
      (let [result (schema/conform-message-map sut/signin-schema (assoc @entity :email "blah"))]
        (should= "must be a valid email" (:email result))))

    (it "valid"
      (should= nil (seq (schema/conform-message-map sut/signin-schema @entity))))

    )

  (context "signup schema"

    (with entity {:password         "password"
                  :confirm-password "password"
                  :email            "blah@blah.com"})

    (it "required"
      (let [result (schema/conform-message-map sut/signup-schema {})]
        (should= "is required" (:email result))
        (should= "is required" (:password result))
        (should= "is required" (:confirm-password result))))

    (it "email format"
      (let [result (schema/conform-message-map sut/signup-schema (assoc @entity :email "blah"))]
        (should= "must be a valid email" (:email result))))

    (it "password length"
      (let [result (schema/conform-message-map sut/signup-schema (assoc @entity :password "blah"))]
        (should= "must have at least 8 characters" (:password result))))

    (it "passwords don't match"
      (let [result (schema/conform-message-map sut/signup-schema (assoc @entity :confirm-password "blah"))]
        (should= "must match password" (:confirm-password result))))

    (it "valid"
      (should= nil (seq (schema/conform-message-map sut/signup-schema @entity))))
    )

  (context "reset password schema"

    (with entity {:password         "password"
                  :confirm-password "password"
                  :recovery-token   (str (random-uuid))})

    (it "required"
      (let [result (schema/conform-message-map sut/reset-password-schema {})]
        (should= "is required" (:recovery-token result))
        (should= "is required" (:password result))
        (should= "is required" (:confirm-password result))))

    (it "password length"
      (let [result (schema/conform-message-map sut/reset-password-schema (assoc @entity :password "blah"))]
        (should= "must have at least 8 characters" (:password result))))

    (it "passwords don't match"
      (let [result (schema/conform-message-map sut/reset-password-schema (assoc @entity :confirm-password "blah"))]
        (should= "must match password" (:confirm-password result))))

    (it "valid"
      (should= nil (seq (schema/conform-message-map sut/reset-password-schema @entity))))
    )
  )
