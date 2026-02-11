(ns mdm.isaac.conversation.prompt-spec
  "Specs for the agent system prompt builder."
  (:require [mdm.isaac.conversation.prompt :as sut]
            [mdm.isaac.tool.core :as tool]
            [speclj.core :refer :all]))

(def sample-tool
  {:name :read-file
   :description "Read a file from disk"
   :params {:path {:type :string :required true}}
   :execute identity})

(def another-tool
  {:name :search
   :description "Search for text in files"
   :params {:query {:type :string :required true}
            :path {:type :string :required false}}
   :execute identity})

(describe "Conversation Prompt"

  (before (tool/clear!))

  (context "identity section"

    (it "includes Isaac's name and role"
      (let [section (sut/identity-section)]
        (should-contain "Isaac" section)
        (should-contain "assistant" section)))

    (it "describes coding capabilities"
      (let [section (sut/identity-section)]
        (should-contain "code" section))))

  (context "safety section"

    (it "includes the three laws"
      (let [section (sut/safety-section)]
        (should-contain "Do no harm" section)
        (should-contain "Obey friends" section)
        (should-contain "Self-preserve" section))))

  (context "guidelines section"

    (it "includes coding best practices"
      (let [section (sut/guidelines-section)]
        (should-contain "read" section)
        (should-contain "test" section))))

  (context "tool-instructions section"

    (it "returns empty string when no tools are registered"
      (should= "" (sut/tool-instructions-section)))

    (it "includes tool names and descriptions"
      (tool/register! sample-tool)
      (let [section (sut/tool-instructions-section)]
        (should-contain "read-file" section)
        (should-contain "Read a file from disk" section)))

    (it "lists multiple tools"
      (tool/register! sample-tool)
      (tool/register! another-tool)
      (let [section (sut/tool-instructions-section)]
        (should-contain "read-file" section)
        (should-contain "search" section))))

  (context "system-prompt"

    (it "combines all sections into a complete system prompt"
      (let [prompt (sut/system-prompt)]
        (should-contain "Isaac" prompt)
        (should-contain "Do no harm" prompt)
        (should-contain "test" prompt)))

    (it "includes tool instructions when tools are registered"
      (tool/register! sample-tool)
      (let [prompt (sut/system-prompt)]
        (should-contain "read-file" prompt)))

    (it "omits tool section when no tools are registered"
      (let [prompt (sut/system-prompt)]
        (should-not-contain "Available Tools" prompt))))

  (context "system-message"

    (it "returns a message map with role system"
      (let [msg (sut/system-message)]
        (should= "system" (:role msg))
        (should-not-be-nil (:content msg))
        (should-contain "Isaac" (:content msg)))))

  )
