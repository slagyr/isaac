(ns mdm.isaac.llm.grok-spec
  (:require [c3kit.apron.utilc :as utilc]
            [c3kit.wire.rest :as rest]
            [mdm.isaac.llm.core :as llm]
            [mdm.isaac.llm.grok]
            [mdm.isaac.secret.core :as secret]
            [mdm.isaac.spec-helper :refer [with-config]]
            [speclj.core :refer :all]
            [speclj.stub :as stub]))

(describe "llm.grok"

  (with-stubs)
  (with-config {:llm {:impl :grok
                      :url "https://api.x.ai/v1"
                      :model "grok-3-latest"}})

  (context "chat"

    (it "sends POST request to Grok chat endpoint with correct payload"
      (let [response-body (utilc/->json {:id "chatcmpl-123"
                                         :object "chat.completion"
                                         :created 1677652288
                                         :model "grok-3-latest"
                                         :choices [{:index 0
                                                    :message {:role "assistant"
                                                              :content "Hello! How can I help you?"}
                                                    :finish_reason "stop"}]
                                         :usage {:prompt_tokens 10
                                                 :completion_tokens 8
                                                 :total_tokens 18}})]
        (with-redefs [rest/post! (stub :rest/post! {:return {:status 200 :body response-body}})
                      secret/get-secret (stub :get-secret {:return "test-api-key"})]
          (should= "Hello! How can I help you?" (llm/chat "Hi there"))
          (should-have-invoked :rest/post!)
          (let [[url options] (stub/last-invocation-of :rest/post!)]
            (should= "https://api.x.ai/v1/chat/completions" url)
            (should= "Bearer test-api-key" (get-in options [:headers "Authorization"]))
            (should= "application/json" (get-in options [:headers "Content-Type"]))
            (should= "grok-3-latest" (get-in options [:body :model]))
            (should= [{:role "user" :content "Hi there"}] (get-in options [:body :messages]))))))

    (it "uses default URL when not configured"
      (with-redefs [rest/post! (stub :rest/post! {:return {:status 200
                                                           :body (utilc/->json {:choices [{:message {:content "ok"}}]})}})
                    secret/get-secret (stub :get-secret {:return "key"})]
        (llm/chat "test")
        (let [[url _] (stub/last-invocation-of :rest/post!)]
          (should-contain "api.x.ai" url))))

    (it "throws on non-200 response"
      (with-redefs [rest/post! (stub :rest/post! {:return {:status 401 :body "Unauthorized"}})
                    secret/get-secret (stub :get-secret {:return "bad-key"})]
        (should-throw clojure.lang.ExceptionInfo
                      "Grok chat request failed"
                      (llm/chat "test")))))

  (context "chat-with-tools"

    (it "sends tools in request and parses tool calls from response"
      (let [response-body (utilc/->json {:id "chatcmpl-456"
                                         :object "chat.completion"
                                         :model "grok-3-latest"
                                         :choices [{:index 0
                                                    :message {:role "assistant"
                                                              :content nil
                                                              :tool_calls [{:id "call_123"
                                                                            :type "function"
                                                                            :function {:name "get_weather"
                                                                                       :arguments "{\"location\":\"San Francisco\"}"}}]}
                                                    :finish_reason "tool_calls"}]})
            tools [{:type "function"
                    :function {:name "get_weather"
                               :description "Get weather for a location"
                               :parameters {:type "object"
                                            :properties {:location {:type "string"}}}}}]]
        (with-redefs [rest/post! (stub :rest/post! {:return {:status 200 :body response-body}})
                      secret/get-secret (stub :get-secret {:return "test-api-key"})]
          (let [result (llm/chat-with-tools "What's the weather in SF?" tools)]
            (should= nil (:content result))
            (should= 1 (count (:tool-calls result)))
            (should= "get_weather" (-> result :tool-calls first :name))
            (should= {:location "San Francisco"} (-> result :tool-calls first :arguments))))))

    (it "returns content when no tool calls"
      (let [response-body (utilc/->json {:choices [{:message {:role "assistant"
                                                              :content "I don't need tools for this."
                                                              :tool_calls nil}}]})]
        (with-redefs [rest/post! (stub :rest/post! {:return {:status 200 :body response-body}})
                      secret/get-secret (stub :get-secret {:return "key"})]
          (let [result (llm/chat-with-tools "Hello" [])]
            (should= "I don't need tools for this." (:content result))
            (should= nil (:tool-calls result))))))

    (it "throws on API error"
      (with-redefs [rest/post! (stub :rest/post! {:return {:status 500 :body "Internal error"}})
                    secret/get-secret (stub :get-secret {:return "key"})]
        (should-throw clojure.lang.ExceptionInfo
                      "Grok chat request failed"
                      (llm/chat-with-tools "test" []))))))
