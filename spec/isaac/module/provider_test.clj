(ns isaac.module.provider-test
  (:require
    [isaac.api :as api]
    [isaac.provider :as provider]))

(deftype TestProvider [name cfg]
  provider/Provider
  (chat [_ _] {:message {:role "assistant" :content "ok"} :model "test" :usage {}})
  (chat-stream [_ _ _] {:message {:role "assistant" :content "ok"} :model "test" :usage {}})
  (followup-messages [_ request _ _ _] (:messages request))
  (config [_] cfg)
  (display-name [_] name))

(defn- make [name cfg]
  (->TestProvider name cfg))

(api/register-provider! "test-api" make)
