(ns isaac.prompt.builder-spec
  (:require
    [isaac.prompt.builder :as sut]
    [speclj.core :refer :all]))

(def sample-tool
  {:name        :read-file
   :description "Read a file from disk"
   :params      {:path {:type :string :required true}}})

(def another-tool
  {:name        :search
   :description "Search for text in files"
   :params      {:query {:type :string :required true}
                 :path  {:type :string :required false}}})

(describe "Prompt Builder"

  (context "identity-section"

    (it "includes Isaac's name"
      (should-contain "Isaac" (sut/identity-section)))

    (it "describes the assistant role"
      (should-contain "assistant" (sut/identity-section)))

    (it "mentions coding capabilities"
      (should-contain "code" (sut/identity-section))))

  (context "safety-section"

    (it "includes the first law - do no harm"
      (should-contain "Do no harm" (sut/safety-section)))

    (it "includes the second law - obey friends"
      (should-contain "Obey friends" (sut/safety-section)))

    (it "includes the third law - self-preserve"
      (should-contain "Self-preserve" (sut/safety-section))))

  (context "guidelines-section"

    (it "mentions reading files before editing"
      (should-contain "read" (sut/guidelines-section)))

    (it "mentions running tests"
      (should-contain "test" (sut/guidelines-section)))

    (it "mentions simplicity"
      (should-contain "simple" (sut/guidelines-section))))

  (context "tool-instructions-section"

    (it "returns empty string when tools is nil"
      (should= "" (sut/tool-instructions-section nil)))

    (it "returns empty string when tools is empty"
      (should= "" (sut/tool-instructions-section [])))

    (it "includes tool name and description for a single tool"
      (let [section (sut/tool-instructions-section [sample-tool])]
        (should-contain "read-file" section)
        (should-contain "Read a file from disk" section)))

    (it "includes the Available Tools header"
      (let [section (sut/tool-instructions-section [sample-tool])]
        (should-contain "Available Tools" section)))

    (it "lists multiple tools"
      (let [section (sut/tool-instructions-section [sample-tool another-tool])]
        (should-contain "read-file" section)
        (should-contain "search" section)
        (should-contain "Search for text" section))))

  (context "system-prompt"

    (it "combines all sections without tools"
      (let [prompt (sut/system-prompt)]
        (should-contain "Isaac" prompt)
        (should-contain "Do no harm" prompt)
        (should-contain "test" prompt)))

    (it "omits tool section when no tools provided"
      (should-not-contain "Available Tools" (sut/system-prompt)))

    (it "omits tool section when tools are empty"
      (should-not-contain "Available Tools" (sut/system-prompt [])))

    (it "includes tool section when tools provided"
      (let [prompt (sut/system-prompt [sample-tool])]
        (should-contain "Available Tools" prompt)
        (should-contain "read-file" prompt)))

    (it "separates sections with double newlines"
      (let [prompt (sut/system-prompt)]
        (should-contain "\n\n" prompt))))

  )
