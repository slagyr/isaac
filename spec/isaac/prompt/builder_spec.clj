(ns isaac.prompt.builder-spec
  (:require
    [isaac.prompt.builder :as sut]
    [speclj.core :refer :all]))

(def sample-transcript
  [{:type "session" :id "sess-1" :timestamp 1000}
   {:type "message" :id "m1" :parentId "sess-1" :timestamp 2000
    :message {:role "user" :content "Hello"}}
   {:type "message" :id "m2" :parentId "m1" :timestamp 3000
    :message {:role "assistant" :content "Hi there"}}])

(def compacted-transcript
  [{:type "session" :id "sess-1" :timestamp 1000}
   {:type "message" :id "m1" :parentId "sess-1" :timestamp 2000
    :message {:role "user" :content "Old message"}}
   {:type "compaction" :id "c1" :parentId "m1" :timestamp 3000
    :summary "User said hello and assistant replied."}
   {:type "message" :id "m3" :parentId "c1" :timestamp 4000
    :message {:role "user" :content "New message"}}])

(def partially-compacted-transcript
  [{:type "session" :id "sess-1" :timestamp 1000}
   {:type "message" :id "m1" :parentId "sess-1" :timestamp 2000
    :message {:role "user" :content "Older question"}}
   {:type "message" :id "m2" :parentId "m1" :timestamp 3000
    :message {:role "assistant" :content "Older answer"}}
   {:type "message" :id "m3" :parentId "m2" :timestamp 4000
    :message {:role "user" :content "Recent question"}}
   {:type "message" :id "m4" :parentId "m3" :timestamp 5000
    :message {:role "assistant" :content "Recent answer"}}
   {:type "compaction" :id "c1" :parentId "m4" :timestamp 6000
    :summary "Older exchange summary"
    :firstKeptEntryId "m3"}
   {:type "message" :id "m5" :parentId "c1" :timestamp 7000
    :message {:role "user" :content "Newest question"}}])

(def tool-transcript
  [{:type "session" :id "sess-1" :timestamp 1000}
   {:type "message" :id "m1" :parentId "sess-1" :timestamp 2000
    :message {:role "user" :content "Read the README"}}
   {:type "message" :id "m2" :parentId "m1" :timestamp 3000
    :message {:role "assistant" :type "toolCall" :id "tc-1" :name "read" :arguments {:filePath "README.md"}}}
   {:type "message" :id "m3" :parentId "m2" :timestamp 4000
    :message {:role "toolResult" :id "tc-1" :content "README contents"}}
   {:type "message" :id "m4" :parentId "m3" :timestamp 5000
    :message {:role "assistant" :content "Here is the README summary."}}])

(describe "Prompt Builder"

  (context "build"

    (it "includes model"
      (let [p (sut/build {:model "qwen3-coder:30b" :soul "You are Isaac." :transcript sample-transcript})]
        (should= "qwen3-coder:30b" (:model p))))

    (it "puts soul as system message first"
      (let [p (sut/build {:model "test" :soul "You are Isaac." :transcript sample-transcript})]
        (should= {:role "system" :content "You are Isaac."} (first (:messages p)))))

    (it "includes transcript messages after system"
      (let [p (sut/build {:model "test" :soul "You are Isaac." :transcript sample-transcript})]
        (should= 3 (count (:messages p)))
        (should= "user" (:role (second (:messages p))))
        (should= "Hello" (:content (second (:messages p))))))

    (it "skips non-message entries"
      (let [p (sut/build {:model "test" :soul "Test." :transcript sample-transcript})]
        (should (every? #(contains? #{"system" "user" "assistant"} (:role %)) (:messages p)))))

    (it "includes tool results as user messages and excludes the preceding user turn and tool call"
      (let [p (sut/build {:model "test" :soul "Test." :transcript tool-transcript})]
        (should= [{:role "system" :content "Test."}
                  {:role "user" :content "README contents"}
                  {:role "assistant" :content "Here is the README summary."}]
                 (:messages p))))

    (it "truncates large tool results when context-window is provided"
      (let [large-content (apply str (repeat 200 "x"))
            large-tool-tr (assoc-in tool-transcript [3 :message :content] large-content)
            p             (sut/build {:model "test" :soul "Test." :transcript large-tool-tr :context-window 100})]
        (should-contain "characters truncated" (get-in p [:messages 1 :content]))))

    (it "includes token estimate"
      (let [p (sut/build {:model "test" :soul "Test." :transcript sample-transcript})]
        (should (pos? (:tokenEstimate p))))))

  (context "compaction"

    (it "uses summary as first user message after compaction"
      (let [p (sut/build {:model "test" :soul "You are Isaac." :transcript compacted-transcript})]
        (should= "system" (:role (first (:messages p))))
        (should= "user" (:role (second (:messages p))))
        (should= "User said hello and assistant replied." (:content (second (:messages p))))))

    (it "includes post-compaction messages"
      (let [p (sut/build {:model "test" :soul "You are Isaac." :transcript compacted-transcript})]
        (should= 3 (count (:messages p)))
        (should= "New message" (:content (nth (:messages p) 2)))))

    (it "includes preserved messages referenced by firstKeptEntryId"
      (let [p (sut/build {:model "test" :soul "You are Isaac." :transcript partially-compacted-transcript})]
        (should= [{:role "system" :content "You are Isaac."}
                  {:role "user" :content "Older exchange summary"}
                  {:role "user" :content "Recent question"}
                  {:role "assistant" :content "Recent answer"}
                  {:role "user" :content "Newest question"}]
                 (:messages p)))))

  (context "tools"

    (it "omits tools key when no tools"
      (let [p (sut/build {:model "test" :soul "Test." :transcript sample-transcript})]
        (should-not-contain :tools p)))

    (it "formats tools for Ollama API"
      (let [tools [{:name "read_file" :description "Read a file" :parameters {:type "object"}}]
            p (sut/build {:model "test" :soul "Test." :transcript sample-transcript :tools tools})]
        (should= 1 (count (:tools p)))
        (should= "function" (:type (first (:tools p))))
        (should= "read_file" (get-in (first (:tools p)) [:function :name])))))

  (context "estimate-tokens"

    (it "returns at least 1"
      (should= 1 (sut/estimate-tokens "")))

    (it "estimates based on chars/4"
      (should= 5 (sut/estimate-tokens (apply str (repeat 20 "a"))))))

  (context "truncate-tool-result"

    (it "returns content unchanged when within limit"
      (let [content "short content"]
        (should= content (sut/truncate-tool-result content 10000))))

    (it "truncates content exceeding max-chars with head-and-tail strategy"
      (let [content (apply str (repeat 200 "x"))
            result  (sut/truncate-tool-result content 100)]
        (should-contain "characters truncated" result)
        (should (< (count result) (count content)))))

    (it "preserves head and tail of the content"
      (let [head    (apply str (repeat 30 "H"))
            middle  (apply str (repeat 100 "M"))
            tail    (apply str (repeat 30 "T"))
            content (str head middle tail)
            result  (sut/truncate-tool-result content 50)]
        (should-contain "HHHHH" result)
        (should-contain "TTTTT" result)))

    (it "includes truncation count in the marker"
      (let [content (apply str (repeat 160 "x"))
            result  (sut/truncate-tool-result content 50)]
        (should-contain "100 characters truncated" result)))

    (it "returns content at exactly the limit unchanged"
      (let [content (apply str (repeat 60 "x"))]
        (should= content (sut/truncate-tool-result content 50)))))

  )
