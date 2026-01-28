(ns mdm.isaac.ollama
  (:require [c3kit.apron.utilc :as util]
            [c3kit.wire.rest :as rest]))

(def ollama-url "http://localhost:11434")
;(def model "llama3.1:8b")
(def model "mistral:latest")

(defn chat [prompt]
  (let [payload  {:model model :messages [{:role "user" :content prompt}] :stream false}
        response (rest/post! (str ollama-url "/api/chat") {:body payload})]
    (if (not= 200 (:status response))
      (throw (ex-info "Ollama chat request failed" {:status (:status response) :body (:body response)}))
      (let [body (-> response :body util/<-json-kw)]
        (-> body :message :content)))))
