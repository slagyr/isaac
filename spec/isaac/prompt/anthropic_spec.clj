(ns isaac.prompt.anthropic-spec
  (:require
    [isaac.prompt.anthropic :as sut]
    [speclj.core :refer :all]))

(def sample-tool
  {:name        :read-file
   :description "Read a file from disk"
   :params      {:path {:type :string :required true}}})

(def multi-param-tool
  {:name        :search
   :description "Search for text in files"
   :params      {:query {:type :string :required true :description "Search query"}
                 :path  {:type :string :required false :description "Directory to search"}}})

(describe "Anthropic Prompt"

  (context "system-message"

    (it "returns a string containing Isaac's identity"
      (should-contain "Isaac" (sut/system-message)))

    (it "returns a string containing safety rules"
      (should-contain "Do no harm" (sut/system-message)))

    (it "includes tool instructions when tools provided"
      (let [msg (sut/system-message [sample-tool])]
        (should-contain "read-file" msg)))

    (it "excludes tool section when no tools"
      (should-not-contain "Available Tools" (sut/system-message))))

  (context "user-message"

    (it "creates a message with role user"
      (should= "user" (:role (sut/user-message "hello"))))

    (it "includes the content"
      (should= "hello" (:content (sut/user-message "hello")))))

  (context "assistant-message"

    (it "creates a message with role assistant"
      (should= "assistant" (:role (sut/assistant-message "hi"))))

    (it "includes the content"
      (should= "hi" (:content (sut/assistant-message "hi")))))

  (context "format-tool"

    (it "converts tool name to string"
      (should= "read-file" (:name (sut/format-tool sample-tool))))

    (it "preserves the description"
      (should= "Read a file from disk" (:description (sut/format-tool sample-tool))))

    (it "creates an input_schema with type object"
      (should= "object" (get-in (sut/format-tool sample-tool) [:input_schema :type])))

    (it "includes properties in the schema"
      (let [props (get-in (sut/format-tool sample-tool) [:input_schema :properties])]
        (should-contain "path" props)))

    (it "includes required params in the required array"
      (let [required (get-in (sut/format-tool sample-tool) [:input_schema :required])]
        (should= ["path"] required)))

    (it "handles multiple params with mixed required"
      (let [formatted (sut/format-tool multi-param-tool)
            required  (get-in formatted [:input_schema :required])
            props     (get-in formatted [:input_schema :properties])]
        (should-contain "query" props)
        (should-contain "path" props)
        (should-contain "query" required)
        (should-not-contain "path" required)))

    (it "includes param descriptions when present"
      (let [props (get-in (sut/format-tool multi-param-tool) [:input_schema :properties])]
        (should= "Search query" (get-in props ["query" :description])))))

  (context "format-tools"

    (it "converts multiple tools"
      (let [formatted (sut/format-tools [sample-tool multi-param-tool])]
        (should= 2 (count formatted))
        (should= "read-file" (:name (first formatted)))
        (should= "search" (:name (second formatted))))))

  (context "chat-request"

    (it "includes the model"
      (let [req (sut/chat-request {:messages [{:role "user" :content "hi"}]})]
        (should= "claude-sonnet-4-20250514" (:model req))))

    (it "allows model override"
      (let [req (sut/chat-request {:model "claude-3-opus" :messages []})]
        (should= "claude-3-opus" (:model req))))

    (it "includes messages"
      (let [msgs [{:role "user" :content "hello"}]
            req  (sut/chat-request {:messages msgs})]
        (should= msgs (:messages req))))

    (it "includes system prompt"
      (let [req (sut/chat-request {:messages []})]
        (should-contain "Isaac" (:system req))))

    (it "allows system prompt override"
      (let [req (sut/chat-request {:system "Custom system" :messages []})]
        (should= "Custom system" (:system req))))

    (it "includes max_tokens with default"
      (let [req (sut/chat-request {:messages []})]
        (should= 4096 (:max_tokens req))))

    (it "allows max_tokens override"
      (let [req (sut/chat-request {:max-tokens 1024 :messages []})]
        (should= 1024 (:max_tokens req))))

    (it "includes formatted tools when provided"
      (let [req (sut/chat-request {:messages [] :tools [sample-tool]})]
        (should= 1 (count (:tools req)))
        (should= "read-file" (:name (first (:tools req))))))

    (it "omits tools key when no tools provided"
      (let [req (sut/chat-request {:messages []})]
        (should-not-contain :tools req))))

  )
