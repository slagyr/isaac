(ns mdm.isaac.embedding-spec
  (:require [c3kit.apron.utilc :as utilc]
            [c3kit.wire.rest :as rest]
            [mdm.isaac.embedding :as sut]
            [mdm.isaac.embedding.ollama]
            [speclj.core :refer :all]
            [speclj.stub :as stub]))


(describe "embedding"

  (with-stubs)

  (context "multimethod dispatch"

    (it "dispatches on provider type"
      (should (instance? clojure.lang.MultiFn sut/embed))))

  (context "ollama provider"

    (it "generates embedding via HTTP"
      (let [body (utilc/->json {:model      "embeddinggemma"
                                :embeddings [[0.1 0.2 0.3]]})]
        (with-redefs [rest/post! (stub :rest/post! {:return {:status 200 :body body}})]
          (should= [0.1 0.2 0.3] (sut/embed :ollama "hello world"))
          (should-have-invoked :rest/post!)
          (let [[url options] (stub/last-invocation-of :rest/post!)]
            (should= "http://localhost:11434/api/embed" url)
            (should= "embeddinggemma" (-> options :body :model))
            (should= "hello world" (-> options :body :input))))))

    (it "throws on HTTP error"
      (with-redefs [rest/post! (stub :rest/post! {:return {:status 500}})]
        (should-throw (sut/embed :ollama "hello world")))))

  (context "onnx provider"

    (it "generates embedding in-process"
      (let [embedding (sut/embed :onnx "hello world")]
        (should (vector? embedding))
        (should= 384 (count embedding))
        (should (every? float? embedding))))

    (it "generates consistent embeddings for same input"
      (let [embedding1 (sut/embed :onnx "test input")
            embedding2 (sut/embed :onnx "test input")]
        (should= embedding1 embedding2)))

    (it "generates different embeddings for different inputs"
      (let [embedding1 (sut/embed :onnx "hello")
            embedding2 (sut/embed :onnx "goodbye")]
        (should-not= embedding1 embedding2))))

  )

