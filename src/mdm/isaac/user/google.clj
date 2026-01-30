(ns mdm.isaac.user.google
  (:require [c3kit.apron.utilc :as utilc]
            [c3kit.wire.rest :as rest]
            [mdm.isaac.user.core :as user]))

(def pkey-host "https://www.googleapis.com/oauth2/v3/certs")

(defmethod user/pkeys :google [_impl]
  (try
    (->> (rest/get! pkey-host nil)
         :body
         utilc/<-json-kw
         :keys
         (reduce (fn [m k] (assoc m (:kid k) k)) {}))
    (catch Exception _
      (throw (ex-info (str "Could not fetch pkeys from " pkey-host) nil)))))
