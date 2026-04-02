(ns isaac.llm.ollama-spec
  (:require
    [cheshire.core :as json]
    [isaac.llm.ollama :as ollama]
    [speclj.core :refer :all]))

(describe "llm.ollama"

  (context "parse-response"

    (it "extracts content from Ollama response"
      (let [body (json/generate-string
                   {:model "mistral:latest"
                    :message {:role "assistant"
                              :content "Hello there!"}
                    :done true})]
        (should= "Hello there!" (ollama/parse-response body))))

    (it "returns nil when content is nil"
      (let [body (json/generate-string
                   {:message {:role "assistant" :content nil}})]
        (should= nil (ollama/parse-response body)))))

  (context "parse-tool-calls"

    (it "returns nil when no tool calls"
      (let [body (json/generate-string
                   {:message {:role "assistant"
                              :content "No tools."}})]
        (should= nil (ollama/parse-tool-calls body))))

    (it "parses tool calls from Ollama response"
      (let [body (json/generate-string
                   {:message {:role "assistant"
                              :content nil
                              :tool_calls [{:function {:name "get_weather"
                                                       :arguments {:location "NYC"}}}]}})]
        (should= [{:name "get_weather" :arguments {:location "NYC"}}]
                 (ollama/parse-tool-calls body))))

    (it "parses multiple tool calls"
      (let [body (json/generate-string
                   {:message {:tool_calls [{:function {:name "fn1" :arguments {:a 1}}}
                                           {:function {:name "fn2" :arguments {:b 2}}}]}})]
        (should= 2 (count (ollama/parse-tool-calls body)))
        (should= "fn1" (-> (ollama/parse-tool-calls body) first :name))
        (should= "fn2" (-> (ollama/parse-tool-calls body) second :name))))))
