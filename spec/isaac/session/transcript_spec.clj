(ns isaac.session.transcript-spec
  (:require
    [isaac.session.transcript :as sut]
    [speclj.core :refer :all]))

(describe "isaac.session.transcript"

  (describe "content->text"

    (it "returns string content as-is"
      (should= "hello" (sut/content->text "hello")))

    (it "joins text blocks from vector content"
      (should= "hello world"
               (sut/content->text [{:type "text" :text "hello "}
                                   {:type "image" :url "https://example.com/cat.png"}
                                   {:type "text" :text "world"}])))

    (it "returns nil for unsupported content shapes"
      (should-be-nil (sut/content->text {:type "text" :text "hello"}))))

  (describe "tool-calls"

    (it "extracts a top-level tool call message"
      (should= [{:type "toolCall" :id "tc-1" :name "read" :arguments {:path "a.txt"}}]
               (sut/tool-calls {:type "toolCall" :id "tc-1" :name "read" :arguments {:path "a.txt"}})))

    (it "extracts tool calls from vector content"
      (should= [{:type "toolCall" :id "tc-2" :name "grep" :arguments {:pattern "lettuce"}}]
               (sut/tool-calls {:content [{:type "toolCall" :id "tc-2" :name "grep" :arguments {:pattern "lettuce"}}]})))

    (it "extracts tool calls from JSON string content"
      (should= [{:type "toolCall" :id "tc-3" :name "exec" :arguments {:command "ls"}}]
               (sut/tool-calls {:content "[{\"type\":\"toolCall\",\"id\":\"tc-3\",\"name\":\"exec\",\"arguments\":{\"command\":\"ls\"}}]"})))

    (it "returns nil for invalid or non-tool-call content"
      (should-be-nil (sut/tool-calls {:content "[not json"}))
      (should-be-nil (sut/tool-calls {:content "plain text"}))))

  (describe "first-tool-call"

    (it "returns the first parsed tool call"
      (should= {:type "toolCall" :id "tc-4" :name "write" :arguments {:content "hi"}}
               (sut/first-tool-call {:content [{:type "toolCall" :id "tc-4" :name "write" :arguments {:content "hi"}}
                                              {:type "toolCall" :id "tc-5" :name "read" :arguments {:path "x"}}]})))

    (it "returns nil when no tool calls are present"
      (should-be-nil (sut/first-tool-call {:content "hello"})))))
