(ns isaac.session.context-steps-spec
  (:require
    [isaac.fs :as fs]
    [isaac.session.context-steps :as sut]
    [isaac.system :as system]
    [speclj.core :refer :all]))

(describe "context feature helpers"

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example]
    (system/with-system {:fs (fs/mem-fs)}
      (example)))

  (it "resolves workspace soul inside the feature filesystem"
    (fs/mkdirs (system/get :fs) "/target/test-state/.isaac/workspace-main")
    (fs/spit   (system/get :fs) "/target/test-state/.isaac/workspace-main/SOUL.md" "You are Dr. Prattlesworth.")
    (let [ctx (sut/-resolve-turn-context {:state-dir "/target/test-state"} "main")]
      (should= "You are Dr. Prattlesworth." (:soul ctx)))))
