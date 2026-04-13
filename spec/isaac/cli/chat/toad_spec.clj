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

  (it "includes --remote in the acp subprocess command"
    (let [args (:args (sut/build-toad-command {:remote "ws://host:6674/acp"}))]
      (should (some #(= "isaac acp --remote ws://host:6674/acp" %) args))))

  (it "includes --token in the acp subprocess command"
    (let [args (:args (sut/build-toad-command {:token "secret123"}))]
      (should (some #(= "isaac acp --token secret123" %) args))))

  (it "includes --resume in the acp subprocess command"
    (let [args (:args (sut/build-toad-command {:resume true}))]
      (should (some #(= "isaac acp --resume" %) args))))

  (it "includes --session in the acp subprocess command"
    (let [args (:args (sut/build-toad-command {:session "agent:main:acp:direct:abc"}))]
      (should (some #(= "isaac acp --session agent:main:acp:direct:abc" %) args))))

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

  (it "returns a string containing remote and token flags"
    (let [s (sut/format-toad-command {:remote "ws://host:6674/acp" :token "secret123"})]
      (should (clojure.string/includes? s "--remote ws://host:6674/acp"))
      (should (clojure.string/includes? s "--token secret123"))))

  (it "returns a string containing resume and session flags"
    (let [s (sut/format-toad-command {:resume true :session "agent:main:acp:direct:abc"})]
      (should (clojure.string/includes? s "--resume"))
      (should (clojure.string/includes? s "--session agent:main:acp:direct:abc"))))

  )
