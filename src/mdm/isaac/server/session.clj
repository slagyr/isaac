(ns mdm.isaac.server.session
  (:require [ring.middleware.session :as ring-session]
            [ring.middleware.session.cookie :refer [cookie-store]]))

(def cookie-name "isaac-session")
(def secret "The day you stop learning is the day you begin decaying.")
(def session-encryption-key (byte-array 16 (.getBytes secret)))
(def config {:store        (cookie-store {:key session-encryption-key})
             :cookie-name  cookie-name
             :cookie-attrs {:http-only true :secure true}})

(defn wrap-session [handler] (ring-session/wrap-session handler config))

(defn copy [response request] (assoc response :session (:session request)))

(defn add [response request key value]
  (let [session (or (:session response) (:session request))]
    (assoc response :session (assoc session key value))))
