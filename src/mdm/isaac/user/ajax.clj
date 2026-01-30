(ns mdm.isaac.user.ajax
  (:require [c3kit.wire.destination :as destination]
            [mdm.isaac.user.core :as user]
            [mdm.isaac.user.web :as web]
            [c3kit.apron.legend :as legend]
            [c3kit.wire.ajax :as ajax]))

(defn handle [request action success-handler]
  (let [result (action (:params request))]
    (if (:errors result)
      (ajax/ok result)
      (success-handler request result))))

(defn- user-response [request user]
  (-> {:user (legend/present! user)}
      ajax/ok
      (web/signin-user request user)))

(defn ajax-signin [request]
  (handle request user/attempt-signin user-response))

(defn ajax-signup [request]
  (handle request user/attempt-signup user-response))

(defn ajax-forgot-password [request]
  (handle request user/attempt-forgot-password (fn [& _] (ajax/ok "ok"))))

(defn ajax-reset-password [request]
  (handle request user/attempt-password-reset user-response))

(defn redirect-with-warning [request url message]
  (-> (ajax/redirect url message)
      (destination/preserve request)))

(defn valid-user? [request]
  (boolean
    (when-let [user (some-> request :user deref)]
      (when (and (:id user) (not (:disabled? user)))
        true))))

(def please-signin "Please sign in to proceed with your request.")

(defmacro ensure-user [request & body]
  `(if (valid-user? ~request)
     (do ~@body)
     (redirect-with-warning ~request "/signin" please-signin)))
