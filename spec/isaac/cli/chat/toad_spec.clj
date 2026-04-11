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

  )

(describe "format-toad-command"

  (it "returns a string containing toad and isaac acp"
    (let [s (sut/format-toad-command)]
      (should (clojure.string/includes? s "toad"))
      (should (clojure.string/includes? s "isaac acp"))))

  )
