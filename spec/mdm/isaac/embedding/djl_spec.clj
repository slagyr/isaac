(ns mdm.isaac.embedding.djl-spec
  (:require [mdm.isaac.config :as config]
            [mdm.isaac.embedding.djl :as sut]
            [mdm.isaac.embedding.core :as core]
            [speclj.core :refer :all]))


(describe "embedding.djl"

  (redefs-around [config/active (merge config/active {:embedding {:impl :djl}})])

  (it "exposes onnx-model-url"
    (should= "djl://ai.djl.huggingface.onnxruntime/sentence-transformers/all-MiniLM-L6-v2"
             sut/onnx-model-url))

  (context "text-embedding :onnx method"

    (it "generates embedding in-process"
      (let [embedding (core/text-embedding "hello world")]
        (should (vector? embedding))
        (should= 384 (count embedding))
        (should (every? float? embedding))))

    (it "generates consistent embeddings for same input"
      (let [embedding1 (core/text-embedding "test input")
            embedding2 (core/text-embedding "test input")]
        (should= embedding1 embedding2)))

    (it "generates different embeddings for different inputs"
      (let [embedding1 (core/text-embedding "hello")
            embedding2 (core/text-embedding "goodbye")]
        (should-not= embedding1 embedding2))))

  )
