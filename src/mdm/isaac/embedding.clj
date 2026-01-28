(ns mdm.isaac.embedding
  (:require [mdm.isaac.embedding.core :as core]
            [mdm.isaac.embedding.ollama])
  (:import [ai.djl.huggingface.translator TextEmbeddingTranslatorFactory]
           [ai.djl.repository.zoo Criteria]))

;; Re-export the embed multimethod from core for backward compatibility
;; TODO - MDM: rename to text-embedding
(def embed core/embed)

;; region ----- onnx -----

;; TODO - MDM: move to mdm.isaac.embedding.djl

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

(defmethod core/embed :onnx [_provider text]
  (let [model (get-or-load-onnx-model)]
    (with-open [predictor (.newPredictor model)]
      (vec (.predict predictor text)))))

;; endregion ^^^^^ onnx ^^^^^
