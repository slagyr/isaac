(ns mdm.isaac.embedding.djl
  (:require [mdm.isaac.embedding.core :as core])
  (:import [ai.djl.huggingface.translator TextEmbeddingTranslatorFactory]
           [ai.djl.repository.zoo Criteria]))

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

(defmethod core/dimensions :djl [] 384)

(defmethod core/text-embedding :djl [text]
  (let [model (get-or-load-onnx-model)]
    (with-open [predictor (.newPredictor model)]
      (vec (.predict predictor text)))))
