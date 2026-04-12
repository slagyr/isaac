(ns isaac.cli.chat.toad-spec
  (:require
    [isaac.cli.chat.toad :as sut]
    [speclj.core :refer :all]))

(describe "build-toad-command"

  (it "uses toad as the command"
    (should= "toad" (:command (sut/build-toad-command))))

  (it "passes isaac acp as the agent command"
    (let [args (:args (sut/build-toad-command))]
      (should (some #(= "isaac acp" %) args))))

  (it "includes --model in the acp subprocess command"
    (let [args (:args (sut/build-toad-command {:model "bosun"}))]
      (should (some #(= "isaac acp --model bosun" %) args))))

  (it "includes --agent in the acp subprocess command"
    (let [args (:args (sut/build-toad-command {:agent "bosun"}))]
      (should (some #(= "isaac acp --agent bosun" %) args))))

  )

(describe "format-toad-command"

  (it "returns a string containing toad and isaac acp"
    (let [s (sut/format-toad-command)]
      (should (clojure.string/includes? s "toad"))
      (should (clojure.string/includes? s "isaac acp"))))

  (it "returns a string containing model and agent flags"
    (let [s (sut/format-toad-command {:model "bosun" :agent "grok"})]
      (should (clojure.string/includes? s "--model bosun"))
      (should (clojure.string/includes? s "--agent grok"))))

  )
