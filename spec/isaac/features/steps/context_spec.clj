(ns isaac.features.steps.context-spec
  (:require
    [isaac.features.steps.context :as sut]
    [isaac.fs :as fs]
    [speclj.core :refer :all]))

(describe "context feature helpers"

  (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

  (it "resolves workspace soul inside the feature filesystem"
    (fs/mkdirs "/target/test-state/.isaac/workspace-main")
    (fs/spit "/target/test-state/.isaac/workspace-main/SOUL.md" "You are Dr. Prattlesworth.")
    (let [ctx (sut/-resolve-turn-context {:state-dir "/target/test-state"} "main")]
      (should= "You are Dr. Prattlesworth." (:soul ctx)))))
