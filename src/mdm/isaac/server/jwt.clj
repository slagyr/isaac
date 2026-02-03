(ns mdm.isaac.server.jwt
      (:require [mdm.isaac.config :as config]
                [c3kit.apron.corec :as ccc]
                [c3kit.apron.time :as time]
                [c3kit.bucket.api :as db]
                [c3kit.wire.jwt :as jwt]
                [clojure.string :as str]))

  (def token-lifetime (* 60 (time/hours 24)))
  (def config {:cookie-name         "isaac-token"
               :refresh-cookie-name "isaac-refresh-token"
               :secret              (:jwt-secret config/active)
               :lifespan            (time/hours 6)
               :refresh-lifespan    token-lifetime
               :secure?             (not config/development?)})

  (defn cookie-value [cookie-name request]
        (get-in request [:cookies cookie-name :value]))

  (defn- <-token [{:keys [cookie-name]} request]
         (or (some-> (get-in request [:headers "authorization"])
                     (str/replace #"Bearer " ""))
             (cookie-value cookie-name request)))

  (defn- <-refresh-token [request {:keys [refresh-cookie-name]}]
         (or (some-> (get-in request [:headers "x-refresh-token"])
                     (str/replace #"Bearer " ""))
             (cookie-value refresh-cookie-name request)))

  (defn assoc-refresh-cookie [{:keys [secret refresh-lifespan domain secure? refresh-cookie-name]} response]
        (let [refresh-token  (jwt/sign (:jwt/payload response) secret refresh-lifespan)
              refresh-cookie (ccc/remove-nils {:value refresh-token :secure (not (false? secure?)) :path "/" :domain domain})]
          (jwt/unsign! refresh-token secret)
          (assoc-in response [:cookies refresh-cookie-name] refresh-cookie)))

  (defn generate-new-access-token [request {:keys [secret lifespan secure? domain cookie-name]}]
        (let [token   (jwt/sign (:jwt/payload request) secret lifespan)
              payload (jwt/unsign! token secret)
              cookie  (ccc/remove-nils {:value token :secure (not (false? secure?)) :path "/" :domain domain})]
          (-> request
              (assoc :jwt/payload payload :jwt/token token)
              (assoc-in [:cookies cookie-name] cookie))))

  (defn decode-jwt [{:keys [secret] :as opts} request]
        (let [token           (<-token opts request)
              refresh-token   (<-refresh-token request opts)
              decoded-access  (jwt/unsign token secret)
              decoded-refresh (jwt/unsign refresh-token secret)]
          (cond decoded-access (-> request (assoc :jwt/payload decoded-access :jwt/token token))
                decoded-refresh (generate-new-access-token (assoc request :jwt/payload decoded-refresh) opts)
                :else (assoc-in request [:jwt/payload :client-id] (jwt/new-client-id)))))

  (defn wrap-jwt [handler]
        (fn [request]
          (let [request (decode-jwt config request)]
            (->> request
                 handler
                 (jwt/sign-response config request)
                 (assoc-refresh-cookie config)))))

  (defn wrap-user [handler]
        (fn [request]
          (handler (assoc request :user (delay (some->> request :jwt/payload :user-id (db/entity :user)))))))
