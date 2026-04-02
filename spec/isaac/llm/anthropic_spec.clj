(ns isaac.llm.anthropic-spec
  (:require
    [cheshire.core :as json]
    [isaac.llm.anthropic :as anthropic]
    [speclj.core :refer :all]))

(describe "llm.anthropic"

  (context "parse-content"

    (it "extracts text from content blocks"
      (let [body (json/generate-string
                   {:id "msg_123"
                    :type "message"
                    :role "assistant"
                    :content [{:type "text" :text "Hello!"}]
                    :stop_reason "end_turn"})]
        (should= "Hello!" (anthropic/parse-content body))))

    (it "returns nil when no text blocks"
      (let [body (json/generate-string
                   {:content [{:type "tool_use"
                               :id "toolu_1"
                               :name "get_weather"
                               :input {:location "SF"}}]})]
        (should= nil (anthropic/parse-content body))))

    (it "returns first text block when multiple present"
      (let [body (json/generate-string
                   {:content [{:type "text" :text "first"}
                              {:type "text" :text "second"}]})]
        (should= "first" (anthropic/parse-content body)))))

  (context "parse-tool-calls"

    (it "returns nil when no tool_use blocks"
      (let [body (json/generate-string
                   {:content [{:type "text" :text "No tools needed."}]})]
        (should= nil (anthropic/parse-tool-calls body))))

    (it "parses single tool_use block"
      (let [body (json/generate-string
                   {:content [{:type "tool_use"
                               :id "toolu_1"
                               :name "get_weather"
                               :input {:location "San Francisco"}}]})]
        (should= [{:name "get_weather" :arguments {:location "San Francisco"}}]
                 (anthropic/parse-tool-calls body))))

    (it "parses multiple tool_use blocks"
      (let [body (json/generate-string
                   {:content [{:type "text" :text "Let me check..."}
                              {:type "tool_use" :id "t1" :name "search" :input {:q "clojure"}}
                              {:type "tool_use" :id "t2" :name "read" :input {:path "/tmp"}}]})]
        (should= 2 (count (anthropic/parse-tool-calls body)))
        (should= "search" (-> (anthropic/parse-tool-calls body) first :name))
        (should= {:q "clojure"} (-> (anthropic/parse-tool-calls body) first :arguments))
        (should= "read" (-> (anthropic/parse-tool-calls body) second :name)))))

  (context "parse-response"

    (it "returns both content and tool-calls"
      (let [body (json/generate-string
                   {:content [{:type "text" :text "I'll help."}
                              {:type "tool_use" :id "t1" :name "do_thing" :input {:x 1}}]})]
        (should= "I'll help." (:content (anthropic/parse-response body)))
        (should= 1 (count (:tool-calls (anthropic/parse-response body))))))

    (it "returns nil tool-calls when only text"
      (let [body (json/generate-string
                   {:content [{:type "text" :text "just text"}]})]
        (should= "just text" (:content (anthropic/parse-response body)))
        (should= nil (:tool-calls (anthropic/parse-response body)))))))
