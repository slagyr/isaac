(ns mdm.isaac.user.corec
  (:require [c3kit.apron.schema :as schema]
            [c3kit.bucket.api :as db]))

  (def email-format {:validate schema/email? :message "must be a valid email"})
  (def password-format {:validate #(> (count %) 7) :message "must have at least 8 characters"})

  ;; TODO - MDM: delete me
  (def signup-email-schema
       {:email {:type :string :validations [email-format]}})

  ;; TODO - MDM: move to clj
  (defn existing-user [email]
        (db/ffind-by :user :email email))

  (def signin-schema
       {:email    {:type :string :validations [schema/required email-format]}
        :password {:type :string :validations [schema/required]}})

  (defn- passwords-match? [signup-entity]
         (= (:password signup-entity) (:confirm-password signup-entity)))

  (def password-match-validation {:validate passwords-match? :message "must match password"})

  (def signup-schema
       (schema/merge-schemas
         signin-schema
         {:password         {:validations [password-format]}
          :confirm-password {:type :string :validations [schema/required]}
          :*                {:confirm-password {:validations [password-match-validation]}}}))

  (def forgot-password-schema (select-keys signup-schema [:email]))

  (def reset-password-schema
       (schema/merge-schemas
         (dissoc signup-schema :email)
         {:recovery-token {:type :uuid :validate schema/present? :message "is required"}}))
