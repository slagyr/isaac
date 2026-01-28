(ns mdm.isaac.embedding.ollama
  (:require [c3kit.apron.utilc :as util]
            [c3kit.wire.rest :as rest]
            [mdm.isaac.embedding.core :as core]))

(def ollama-url "http://localhost:11434")
(def ollama-model "embeddinggemma")

;; embeddings have 768 dimensions
(defmethod core/text-embedding :ollama [text]
  (let [payload  {:model ollama-model :input text}
        response (rest/post! (str ollama-url "/api/embed") {:body payload})]
    (if (not= 200 (:status response))
      (throw (ex-info "Ollama embedding request failed" {:status (:status response) :body (:body response)}))
      (let [body (-> response :body util/<-json-kw)]
        (-> body :embeddings first)))))
