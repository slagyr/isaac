(ns mdm.isaac.embedding
  (:require [c3kit.apron.utilc :as util]
            [c3kit.wire.rest :as rest])
  (:import [ai.djl.huggingface.translator TextEmbeddingTranslatorFactory]
           [ai.djl.repository.zoo Criteria]))

(defmulti embed
  "Generate an embedding vector for the given text using the specified provider.

   Supported providers:
   - :ollama - Uses Ollama's embedding API (requires Ollama running locally, 768 dims)
   - :onnx   - Uses DJL/ONNX in-process model (no external service, 384 dims)

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

;; region ----- onnx -----

(def onnx-model-url "djl://ai.djl.huggingface.onnxruntime/sentence-transformers/all-MiniLM-L6-v2")

(defonce ^:private onnx-model-atom (atom nil))

(defn- get-or-load-onnx-model
  "Lazily loads and caches the ONNX model with TextEmbeddingTranslatorFactory."
  []
  (or @onnx-model-atom
      (let [criteria (-> (Criteria/builder)
                         (.setTypes String (Class/forName "[F"))
                         (.optModelUrls onnx-model-url)
                         (.optEngine "OnnxRuntime")
                         (.optTranslatorFactory (TextEmbeddingTranslatorFactory.))
                         (.build))
            model (.loadModel criteria)]
        (reset! onnx-model-atom model)
        model)))

(defmethod embed :onnx [_provider text]
  (let [model (get-or-load-onnx-model)]
    (with-open [predictor (.newPredictor model)]
      (vec (.predict predictor text)))))

;; endregion ^^^^^ onnx ^^^^^
