(ns mdm.isaac.embedding
  (:require [c3kit.apron.utilc :as util]
            [c3kit.wire.rest :as rest]))

(defmulti embed
  "Generate an embedding vector for the given text using the specified provider.

   Supported providers:
   - :ollama - Uses Ollama's embedding API (requires Ollama running locally)

   Returns a vector of floats representing the embedding."
  (fn [provider _text] provider))

;; region ----- ollama -----

(def ollama-url "http://localhost:11434")
(def ollama-model "embeddinggemma")

(defmethod embed :ollama [_provider text]
  (let [payload  {:model ollama-model :input text}
        response (rest/post! (str ollama-url "/api/embed") {:body payload})]
    (if (not= 200 (:status response))
      (throw (ex-info "Ollama embedding request failed" {:status (:status response) :body (:body response)}))
      (let [body (-> response :body util/<-json-kw)]
        (-> body :embeddings first)))))

;; endregion ^^^^^ ollama ^^^^^
