(ns mdm.isaac.llm.ollama-spec
  (:require [c3kit.apron.utilc :as utilc]
            [c3kit.wire.rest :as rest]
            [mdm.isaac.llm.core :as llm]
            [mdm.isaac.llm.ollama]
            [mdm.isaac.spec-helper :refer [with-config]]
            [speclj.core :refer :all]
            [speclj.stub :as stub]))

(describe "llm.ollama"

  (with-stubs)
  (with-config {:llm {:impl :ollama
                      :url "http://localhost:11434"
                      :model "mistral:latest"}})

  (context "chat-with-tools"

    (it "passes messages vector directly to Ollama API"
      (let [response-body (utilc/->json {:model "mistral:latest"
                                         :message {:role "assistant"
                                                   :content "Hello!"}
                                         :done true})
            messages [{:role "system" :content "You are helpful."}
                      {:role "user" :content "Hi there"}]]
        (with-redefs [rest/post! (stub :rest/post! {:return {:status 200 :body response-body}})]
          (llm/chat-with-tools messages [])
          (let [[_url options] (stub/last-invocation-of :rest/post!)
                body (:body options)]
            (should= messages (:messages body))))))

    (it "includes tools in request"
      (let [response-body (utilc/->json {:model "mistral:latest"
                                         :message {:role "assistant"
                                                   :content "OK"}
                                         :done true})
            tools [{:type "function"
                    :function {:name "get_weather"
                               :description "Get weather"
                               :parameters {:type "object"
                                            :properties {:location {:type "string"}}}}}]]
        (with-redefs [rest/post! (stub :rest/post! {:return {:status 200 :body response-body}})]
          (llm/chat-with-tools [{:role "user" :content "weather?"}] tools)
          (let [[_url options] (stub/last-invocation-of :rest/post!)
                body (:body options)]
            (should= tools (:tools body))))))

    (it "returns content and tool-calls"
      (let [response-body (utilc/->json {:model "mistral:latest"
                                         :message {:role "assistant"
                                                   :content nil
                                                   :tool_calls [{:function {:name "get_weather"
                                                                            :arguments {:location "SF"}}}]}
                                         :done true})]
        (with-redefs [rest/post! (stub :rest/post! {:return {:status 200 :body response-body}})]
          (let [result (llm/chat-with-tools [{:role "user" :content "weather in SF?"}]
                                            [{:type "function"}])]
            (should= nil (:content result))
            (should= 1 (count (:tool-calls result)))
            (should= "get_weather" (-> result :tool-calls first :name))))))

    (it "throws on non-200 response"
      (with-redefs [rest/post! (stub :rest/post! {:return {:status 500 :body "Internal error"}})]
        (should-throw clojure.lang.ExceptionInfo
                      "Ollama chat request failed"
                      (llm/chat-with-tools [{:role "user" :content "test"}] []))))))
