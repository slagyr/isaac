(ns isaac.llm.openai-compat-spec
  (:require
    [cheshire.core :as json]
    [isaac.llm.openai-compat :as compat]
    [speclj.core :refer :all]))

(describe "llm.openai-compat"

  (context "parse-content"

    (it "extracts content from standard chat completion"
      (let [body (json/generate-string
                   {:choices [{:message {:role "assistant"
                                         :content "Hello!"}}]})]
        (should= "Hello!" (compat/parse-content body))))

    (it "returns nil when content is null"
      (let [body (json/generate-string
                   {:choices [{:message {:role "assistant"
                                         :content nil}}]})]
        (should= nil (compat/parse-content body))))

    (it "takes first choice"
      (let [body (json/generate-string
                   {:choices [{:message {:content "first"}}
                              {:message {:content "second"}}]})]
        (should= "first" (compat/parse-content body)))))

  (context "parse-tool-calls"

    (it "returns nil when no tool calls"
      (let [body (json/generate-string
                   {:choices [{:message {:role "assistant"
                                         :content "No tools needed."}}]})]
        (should= nil (compat/parse-tool-calls body))))

    (it "returns nil when tool_calls is empty"
      (let [body (json/generate-string
                   {:choices [{:message {:role "assistant"
                                         :content "ok"
                                         :tool_calls []}}]})]
        (should= nil (compat/parse-tool-calls body))))

    (it "parses single tool call with string arguments"
      (let [body (json/generate-string
                   {:choices [{:message {:role "assistant"
                                         :content nil
                                         :tool_calls [{:id "call_1"
                                                       :type "function"
                                                       :function {:name "get_weather"
                                                                  :arguments "{\"location\":\"SF\"}"}}]}}]})]
        (should= [{:name "get_weather" :arguments {:location "SF"}}]
                 (compat/parse-tool-calls body))))

    (it "parses tool call with map arguments (already parsed)"
      (let [body (json/generate-string
                   {:choices [{:message {:tool_calls [{:function {:name "search"
                                                                  :arguments {:query "test"}}}]}}]})]
        (should= [{:name "search" :arguments {:query "test"}}]
                 (compat/parse-tool-calls body))))

    (it "parses multiple tool calls"
      (let [body (json/generate-string
                   {:choices [{:message {:tool_calls [{:function {:name "fn1"
                                                                  :arguments "{\"a\":1}"}}
                                                      {:function {:name "fn2"
                                                                  :arguments "{\"b\":2}"}}]}}]})]
        (should= 2 (count (compat/parse-tool-calls body)))
        (should= "fn1" (-> (compat/parse-tool-calls body) first :name))
        (should= "fn2" (-> (compat/parse-tool-calls body) second :name)))))

  (context "parse-response"

    (it "returns both content and tool-calls"
      (let [body (json/generate-string
                   {:choices [{:message {:content "thinking..."
                                         :tool_calls [{:function {:name "do_thing"
                                                                  :arguments "{}"}}]}}]})]
        (should= "thinking..." (:content (compat/parse-response body)))
        (should= 1 (count (:tool-calls (compat/parse-response body))))))

    (it "returns nil tool-calls when none present"
      (let [body (json/generate-string
                   {:choices [{:message {:content "just text"}}]})]
        (should= "just text" (:content (compat/parse-response body)))
        (should= nil (:tool-calls (compat/parse-response body)))))))
