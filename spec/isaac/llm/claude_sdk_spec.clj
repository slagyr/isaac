(ns isaac.llm.claude-sdk-spec
  (:require
    [cheshire.core :as json]
    [isaac.llm.claude-sdk :as sut]
    [speclj.core :refer :all]))

(describe "Claude SDK Client"

  (describe "parse-stream-event"

    (it "extracts text from assistant message"
      (let [event {:type "assistant"
                   :message {:content [{:type "text" :text "Hello"}]
                             :model "claude-sonnet-4-6"}}
            acc   {:content "" :model nil :usage {}}
            result (sut/parse-stream-event event acc)]
        (should= "Hello" (:content result))
        (should= "claude-sonnet-4-6" (:model result))))

    (it "accumulates text across events"
      (let [acc {:content "Hello" :model "claude-sonnet-4-6" :usage {}}
            event {:type "assistant"
                   :message {:content [{:type "text" :text " world"}]}}
            result (sut/parse-stream-event event acc)]
        (should= "Hello world" (:content result))))

    (it "extracts usage from result event"
      (let [event {:type "result"
                   :usage {:input_tokens 10
                           :cache_read_input_tokens 5
                           :cache_creation_input_tokens 3
                           :output_tokens 8}}
            acc   {:content "Hi" :model "claude-sonnet-4-6" :usage {}}
            result (sut/parse-stream-event event acc)]
        (should= 10 (get-in result [:usage :input_tokens]))
        (should= 8 (get-in result [:usage :output_tokens]))))

    (it "ignores system events"
      (let [event {:type "system" :subtype "init"}
            acc   {:content "x" :model "m" :usage {}}
            result (sut/parse-stream-event event acc)]
        (should= acc result))))

  (describe "parse-usage"

    (it "maps SDK usage fields to Isaac token fields"
      (let [usage {:input_tokens 100
                   :output_tokens 50
                   :cache_read_input_tokens 20
                   :cache_creation_input_tokens 10}
            result (sut/parse-usage usage)]
        (should= 100 (:inputTokens result))
        (should= 50 (:outputTokens result))
        (should= 20 (:cacheRead result))
        (should= 10 (:cacheWrite result))))

    (it "defaults missing fields to 0"
      (let [result (sut/parse-usage {})]
        (should= 0 (:inputTokens result))
        (should= 0 (:outputTokens result)))))

  (describe "build-args"

    (it "builds minimal args for a simple request"
      (let [args (sut/build-args {:model "claude-sonnet-4-6"
                                   :messages [{:role "user" :content "Hi"}]}
                                  {})]
        (should-contain "-p" args)
        (should-contain "--no-session-persistence" args)
        (should-contain "--output-format" args)
        (should-contain "json" args)
        (should-contain "--model" args)
        (should-contain "sonnet" args)))

    (it "includes system prompt when provided"
      (let [args (sut/build-args {:model "claude-sonnet-4-6"
                                   :system [{:type "text" :text "You are Isaac."}]
                                   :messages [{:role "user" :content "Hi"}]}
                                  {})]
        (should-contain "--system-prompt" args)))

    (it "uses stream-json format when streaming"
      (let [args (sut/build-args {:model "claude-sonnet-4-6"
                                   :messages [{:role "user" :content "Hi"}]}
                                  {:stream true})]
        (should-contain "stream-json" args)
        (should-contain "--verbose" args))))

  (describe "extract-prompt"

    (it "extracts last user message content"
      (should= "Hello" (sut/extract-prompt [{:role "system" :content "Soul"}
                                              {:role "user" :content "Hello"}])))

    (it "extracts last user message when multiple"
      (should= "Second" (sut/extract-prompt [{:role "user" :content "First"}
                                               {:role "assistant" :content "Reply"}
                                               {:role "user" :content "Second"}]))))

  (describe "map-model-alias"

    (it "maps claude-sonnet-4-6 to sonnet"
      (should= "sonnet" (sut/map-model-alias "claude-sonnet-4-6")))

    (it "maps claude-opus-4-6 to opus"
      (should= "opus" (sut/map-model-alias "claude-opus-4-6")))

    (it "maps claude-haiku-4-5-20251001 to haiku"
      (should= "haiku" (sut/map-model-alias "claude-haiku-4-5-20251001")))

    (it "passes through unknown models"
      (should= "custom-model" (sut/map-model-alias "custom-model")))))
