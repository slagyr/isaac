(ns mdm.isaac.user.core
  (:require [c3kit.apron.log :as log]
            [c3kit.apron.schema :as schema]
            [c3kit.bucket.api :as db]
            [clojure.string :as str]
            [mdm.isaac.config :as config]
            [mdm.isaac.email :as email]
            [mdm.isaac.user.corec :as userc])
  (:import (org.mindrot.jbcrypt BCrypt)))

;; region ----- request -----

;; MDM - This feels like it belongs in user.web, however that creates a circular dependency: layouts -> user.web -> layouts

(defn id [req] (-> req :jwt/payload :user-id))

(defn load-from-request [request]
  (when-let [id (id request)]
    (db/entity id)))

(defn current [request]
  (if-let [user (:user request)]
    @user
    (load-from-request request)))

;; endregion ^^^^^ request ^^^^^

(defmulti pkeys identity)

(defn hash-password [password] (BCrypt/hashpw password (BCrypt/gensalt 11)))
(defn check-password [password hash] (BCrypt/checkpw password hash))

(defn verify-password [password hash]
  (if-not (and password hash)
    false
    (check-password password hash)))

(defn authorized-user [{:keys [email password] :as _credentials}]
  (when (and email password)
    (let [user (db/ffind-by :user :email (str/lower-case email) :confirmation-token nil)]
      (when (verify-password password (:password user))
        user))))

(defn- lower-case [s] (when s (str/lower-case s)))
(def lower-case-email {:email {:coerce lower-case}})
(def signin-schema (schema/merge-schemas userc/signin-schema lower-case-email))

(defn attempt-signin
  "Returns the user of the credentials are valid, otherwise a {:errors {}} map."
  [credentials]
  (let [credentials (schema/conform signin-schema credentials)
        user        (delay (db/ffind-by :user :email (:email credentials)))]
    (or (when (schema/error? credentials) {:errors (schema/message-map credentials)})
        (when (nil? @user) {:errors {:email "invalid credentials"}})
        (when (not (check-password (:password credentials) (:password @user))) {:errors {:email "invalid credentials"}})
        (when (:confirmation-token @user) {:errors {:email "check your email for verification link"}})
        @user)))

;; region ----- signup -----

(defn maybe-email-used [{:keys [email]}]
  (when (db/ffind-by :user :email email)
    {:errors {:email "already in use"}}))

(def max-req-per-hr 25)

(defn confirmation-email [to link]
  {:to      to
   :from    config/admin-email
   :subject "Airworthy: Confirm your new account"
   :text    (str "Click this link to begin using your new Airworthy account:\n\n" link)})

(defn new-user [email password token]
  {:kind                :user
   :email               email
   :password            (hash-password password)
   :max-requests-per-hr max-req-per-hr
   :confirmation-token  token})

(defn signup! [{:keys [email password] :as _entity}]
  (let [token (random-uuid)
        user  (db/tx (new-user email password token))]
    (email/send! (confirmation-email (:email user) (config/link "/account/verify/" token)))
    user))

(def signup-schema (schema/merge-schemas userc/signup-schema lower-case-email))

(defn attempt-signup
  "Returns the new user of the credentials are valid, otherwise a {:errors {}} map."
  [signup-entity]
  (let [conformed (schema/conform signup-schema signup-entity)]
    (or (when (schema/error? conformed) {:errors (schema/message-map conformed)})
        (maybe-email-used conformed)
        (signup! conformed))))

;; endregion ^^^^^ signup ^^^^^

;; region ----- recover -----

(def recovery-request-schema
  {:email {:type :string :validations [schema/required userc/email-format]}})

(defn recovery-email [to permalink]
  {:to      to
   :from    config/admin-email
   :subject "Airworthy: Recover your account"
   :text    (str "Click this link to recover your Airworthy account:\n\n" (config/link "/recover/" permalink))})

(defn recover! [email]
  (if-let [user (db/ffind-by :user :email email)]
    (let [recovery-token (random-uuid)
          recovery-email (recovery-email email recovery-token)]
      (db/tx user :recovery-token recovery-token)
      (email/send! recovery-email))
    (log/warn "attempted account recovery for non-existent user" email))
  {})

(defn attempt-forgot-password [recover-entity]
  (let [conformed (schema/conform recovery-request-schema recover-entity)
        email     (:email conformed)]
    (or (when (schema/error? conformed) {:errors (schema/message-map conformed)})
        (recover! email))))

;; endregion ^^^^^ recover ^^^^^

;; region ----- reset password -----

(defn attempt-password-reset [request]
  (let [conformed (schema/conform userc/reset-password-schema request)
        user      (delay (db/ffind-by :user :recovery-token (:recovery-token conformed)))]
    (or (when (schema/error? conformed) {:errors (schema/message-map conformed)})
        (when-not @user {:errors {:recovery-token "is missing, expired, or used"}})
        (let [hash (hash-password (:password conformed))]
          (db/tx @user :password hash :recovery-token nil)))))

;; endregion ^^^^^ reset password ^^^^^
