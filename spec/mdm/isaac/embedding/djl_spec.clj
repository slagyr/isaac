(ns mdm.isaac.embedding.djl-spec
  (:require [mdm.isaac.embedding.djl :as sut]
            [mdm.isaac.embedding.core :as core]
            [speclj.core :refer :all]))


(describe "embedding.djl"

  (it "exposes onnx-model-url"
    (should= "djl://ai.djl.huggingface.onnxruntime/sentence-transformers/all-MiniLM-L6-v2"
             sut/onnx-model-url))

  (context "embed :onnx method"

    (it "generates embedding in-process"
      (let [embedding (core/embed :onnx "hello world")]
        (should (vector? embedding))
        (should= 384 (count embedding))
        (should (every? float? embedding))))

    (it "generates consistent embeddings for same input"
      (let [embedding1 (core/embed :onnx "test input")
            embedding2 (core/embed :onnx "test input")]
        (should= embedding1 embedding2)))

    (it "generates different embeddings for different inputs"
      (let [embedding1 (core/embed :onnx "hello")
            embedding2 (core/embed :onnx "goodbye")]
        (should-not= embedding1 embedding2))))

  )
