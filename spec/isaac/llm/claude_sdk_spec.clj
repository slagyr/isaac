(ns isaac.llm.claude-sdk-spec
  (:require
    [babashka.process :as process]
    [c3kit.apron.schema :as schema]
    [cheshire.core :as json]
    [isaac.llm.api.claude-sdk :as sut]
    [isaac.llm.api :as api]
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
        (should= 100 (:input-tokens result))
        (should= 50 (:output-tokens result))
        (should= 20 (:cache-read result))
        (should= 10 (:cache-write result))))

    (it "defaults missing fields to 0"
      (let [result (sut/parse-usage {})]
        (should= 0 (:input-tokens result))
        (should= 0 (:output-tokens result)))))

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
      (should= "custom-model" (sut/map-model-alias "custom-model"))))

  (describe "chat-stream"

    (it "streams assistant text and returns final usage"
      (let [chunks   (atom [])
            stream   (str (json/generate-string {:type    "assistant"
                                                 :message {:content [{:type "text" :text "Hello"}]
                                                           :model   "claude-sonnet-4-6"}})
                          "\n\n"
                          "not-json\n"
                          (json/generate-string {:type    "assistant"
                                                 :message {:content [{:type "text" :text " world"}]}})
                          "\n"
                          (json/generate-string {:type  "result"
                                                 :usage {:input_tokens 11 :output_tokens 7}})
                          "\n")]
        (with-redefs [process/process (fn [& _]
                                        {:out (java.io.ByteArrayInputStream. (.getBytes stream))})]
          (let [result (sut/chat-stream {:model "claude-sonnet-4-6"
                                         :messages [{:role "user" :content "Hi"}]}
                                        #(swap! chunks conj %))]
            (should= {:delta {:text "Hello"}} (first @chunks))
            (should= {:delta {:text " world"}} (second @chunks))
            (should= "Hello world" (get-in result [:message :content]))
            (should= "claude-sonnet-4-6" (:model result))
            (should= 11 (get-in result [:usage :input-tokens]))
            (should= 7 (get-in result [:usage :output-tokens]))))))

    (it "returns unknown when starting the claude process fails"
      (with-redefs [process/process (fn [& _] (throw (ex-info "boom" {})))]
        (let [result (sut/chat-stream {:model "claude-sonnet-4-6"
                                       :messages [{:role "user" :content "Hi"}]}
                                      identity)]
          (should= :unknown (:error result))
          (should-contain "boom" (:message result))))))

  (describe "chat"

    (it "returns assistant content and usage from claude cli output"
      (let [output (json/generate-string {:result     "Hello from Claude"
                                          :modelUsage {:sonnet {}}
                                          :usage      {:input_tokens 9 :output_tokens 4}})]
        (with-redefs [process/shell (fn [& _] {:out output :err ""})]
          (let [result (sut/chat {:model "claude-sonnet-4-6"
                                  :messages [{:role "user" :content "Hi"}]})]
            (should= "Hello from Claude" (get-in result [:message :content]))
            (should= "sonnet" (:model result))
            (should= 9 (get-in result [:usage :input-tokens]))
            (should= 4 (get-in result [:usage :output-tokens]))))))

    (it "returns sdk-error when claude reports an error result"
      (let [output (json/generate-string {:is_error true :result "Nope"})]
        (with-redefs [process/shell (fn [& _] {:out output :err ""})]
          (let [result (sut/chat {:model "claude-sonnet-4-6"
                                  :messages [{:role "user" :content "Hi"}]})]
            (should= :sdk-error (:error result))
            (should= "Nope" (:message result))))))

    (it "returns unknown when the claude shell call fails"
      (with-redefs [process/shell (fn [& _] (throw (ex-info "kaboom" {})))]
        (let [result (sut/chat {:model "claude-sonnet-4-6"
                                :messages [{:role "user" :content "Hi"}]})]
          (should= :unknown (:error result))
          (should-contain "kaboom" (:message result))))))

  (describe "schema conformance"

    (it "chat returns a value conforming to provider/response"
      (let [output (json/generate-string {:result     "Hello from Claude"
                                          :modelUsage {:sonnet {}}
                                          :usage      {:input_tokens 9 :output_tokens 4}})]
        (with-redefs [process/shell (fn [& _] {:out output :err ""})]
          (let [result (sut/chat {:model "claude-sonnet-4-6" :messages [{:role "user" :content "Hi"}]})]
            (should-not (api/error? result))
            (should-not-throw (api/validate-response result))))))

    (it "chat-stream returns a value conforming to provider/response"
      (let [stream (str (json/generate-string {:type    "assistant"
                                               :message {:content [{:type "text" :text "Hello"}]
                                                         :model   "claude-sonnet-4-6"}})
                        "\n"
                        (json/generate-string {:type  "result"
                                               :usage {:input_tokens 5 :output_tokens 3}})
                        "\n")]
        (with-redefs [process/process (fn [& _]
                                        {:out (java.io.ByteArrayInputStream. (.getBytes stream))})]
          (let [result (sut/chat-stream {:model "claude-sonnet-4-6" :messages []} identity)]
            (should-not (api/error? result))
            (should-not-throw (api/validate-response result))))))

    (it "process spawn errors conform to provider/error-response"
      (with-redefs [process/shell (fn [& _] (throw (ex-info "boom" {})))]
        (let [result (sut/chat {:model "claude-sonnet-4-6" :messages []})]
          (should (api/error? result))
          (should-not-throw (schema/conform! api/error-response result)))))))
