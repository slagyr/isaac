(ns isaac.features.steps.session-spec
  (:require
    [gherclj.core :as g]
    [isaac.config.change-source :as change-source]
    [isaac.features.steps.session :as sut]
    [isaac.fs :as fs]
    [speclj.core :refer :all]))

(describe "session feature steps"

  (around [it]
    (g/reset!)
    (binding [fs/*fs* (fs/mem-fs)]
      (it))
    (g/reset!))

  (it "fires the config change source when a file is written"
    (let [source (change-source/memory-source "/target/test-state")]
      (change-source/start! source)
      (g/assoc! :mem-fs fs/*fs*)
      (g/assoc! :config-change-source source)
      (sut/file-exists-with "/target/test-state/.isaac/config/crew/marvin.edn" "{:model :llama}")
      (should= "crew/marvin.edn" (change-source/poll! source 0))
      (change-source/stop! source))))
