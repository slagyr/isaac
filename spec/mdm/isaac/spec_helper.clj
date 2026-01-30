(ns mdm.isaac.spec-helper
  (:require [c3kit.apron.log :as log]
            [mdm.isaac.config :as config]
            [mdm.isaac.email :as email]
            [mdm.isaac.user.core :as user]
            [mdm.isaac.init :as init]
            [speclj.core :refer :all]))

(log/warn!)
(init/install-legend!)
(init/configure-api!)

(defmacro with-config
  "Temporarily merges config-overrides into config/active for the duration of the test context.
   Usage: (with-config {:embedding {:impl :mock}})"
  [config-overrides]
  `(speclj.core/redefs-around [config/active (merge config/active ~config-overrides)]))

(defn speedy-hash [pw] (str "*hash*" pw "*hash*"))

(defn with-fast-password-hash []
  (around [it]
          (with-redefs [user/hash-password  speedy-hash
                        user/check-password (fn [pw hash] (= (speedy-hash pw) hash))]
            (it))))

(defn stub-email [] (redefs-around [email/send! (stub :email/send!)]))

(defmacro should-flash
  ([level response] `(should-flash ~level nil ~response))
  ([level msg response]
   `(let [flash# (-> ~response :flash :messages first)]
      (should= ~level (:level flash#))
      (when ~msg (should= ~msg (:text flash#))))))

(defmacro should-flash-error
  ([response] `(should-flash :error ~response))
  ([msg response] `(should-flash :error ~msg ~response)))

(defmacro should-flash-warn
  ([response] `(should-flash :warn ~response))
  ([msg response] `(should-flash :warn ~msg ~response)))

(defmacro should-flash-success
  ([response] `(should-flash :success ~response))
  ([msg response] `(should-flash :success ~msg ~response)))
