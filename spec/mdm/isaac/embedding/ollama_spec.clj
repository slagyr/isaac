(ns mdm.isaac.embedding.ollama-spec
  (:require [c3kit.apron.utilc :as utilc]
            [c3kit.wire.rest :as rest]
            [mdm.isaac.embedding.ollama :as sut]
            [mdm.isaac.embedding.core :as core]
            [speclj.core :refer :all]
            [speclj.stub :as stub]))


(describe "embedding.ollama"

  (with-stubs)

  (it "exposes ollama-url"
    (should= "http://localhost:11434" sut/ollama-url))

  (it "exposes ollama-model"
    (should= "embeddinggemma" sut/ollama-model))

  (context "embed :ollama method"

    (it "generates embedding via HTTP"
      (let [body (utilc/->json {:model      "embeddinggemma"
                                :embeddings [[0.1 0.2 0.3]]})]
        (with-redefs [rest/post! (stub :rest/post! {:return {:status 200 :body body}})]
          (should= [0.1 0.2 0.3] (core/embed :ollama "hello world"))
          (should-have-invoked :rest/post!)
          (let [[url options] (stub/last-invocation-of :rest/post!)]
            (should= "http://localhost:11434/api/embed" url)
            (should= "embeddinggemma" (-> options :body :model))
            (should= "hello world" (-> options :body :input))))))

    (it "throws on HTTP error"
      (with-redefs [rest/post! (stub :rest/post! {:return {:status 500}})]
        (should-throw (core/embed :ollama "hello world")))))

  )
