(ns isaac.api.session
  (:require
    [isaac.session.storage :as impl]))

(def create-session! impl/create-session!)
(def get-session    impl/get-session)
