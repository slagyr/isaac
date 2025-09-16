(ns mdm.isaac.ollama-spec
  (:require [c3kit.apron.utilc :as utilc]
            [c3kit.wire.rest :as rest]
            [mdm.isaac.ollama :as sut]
            [speclj.core :refer :all]
            [speclj.stub :as stub]))


(describe "ollama"

  (with-stubs)

  (context "chat"

    (it "request"
      (let [body (utilc/->json {:eval_duration        1016084791
                                :done                 true
                                :load_duration        15411708
                                :eval_count           66
                                :prompt_eval_duration 120274542
                                :total_duration       1152137917
                                :done_reason          "stop"
                                :created_at           "2025-09-13T14:41:19.703405Z"
                                :message              {:role "assistant" :content "bar"}
                                :model                "mistral:latest"
                                :prompt_eval_count    8})]
        (with-redefs [rest/post! (stub :rest/post! {:return {:status 200 :body body}})]
          (should= "bar" (sut/chat "foo"))
          (should-have-invoked :rest/post!)
          (let [[url options] (stub/last-invocation-of :rest/post!)]
            (should= "http://localhost:11434/api/chat" url)
            (should= "user" (-> options :body :messages first :role))
            (should= "foo" (-> options :body :messages first :content))
            (should= sut/model (-> options :body :model))
            (should= false (-> options :body :stream))))))

    (it "error"
      (with-redefs [rest/post! (stub :rest/post! {:return {:status 404}})]
        (should-throw (sut/chat "foo"))))

    #_(it "chat"
      (let [result (sut/chat "Hello, Isaac!")]
        (should= "Hello, Isaac!" result)))

    )

  (context "embedding"
    (it "request"
      (let [body (utilc/->json {:model "embeddinggemma"
                                :embeddings [[0.1 0.2 0.3]]
                                :total_duration 105826791
                                :load_duration 52353208
                                :prompt_eval_count 6})]
        (with-redefs [rest/post! (stub :rest/post! {:return {:status 200 :body body}})]
          (should= [0.1 0.2 0.3] (sut/embedding "foo"))
          (should-have-invoked :rest/post!)
          (let [[url options] (stub/last-invocation-of :rest/post!)]
            (should= "http://localhost:11434/api/embed" url)
            (should= "embeddinggemma" (-> options :body :model))
            (should= "foo" (-> options :body :input))))))

    (it "error"
      (with-redefs [rest/post! (stub :rest/post! {:return {:status 404}})]
        (should-throw (sut/embedding "foo"))))

    #_(focus-it "real call"
      (let [result (sut/embedding "I can haz cheeseburger?")]
        (should= 768 (count (first result)))))

    )



  )

