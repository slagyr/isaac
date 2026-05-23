(ns isaac.session.session-steps-spec
  (:require
    [gherclj.core :as g]
    [isaac.config.change-source :as change-source]
    [isaac.fs :as fs]
    [isaac.marigold :as marigold]
    [isaac.session.session-steps :as sut]
    [isaac.nexus :as nexus]
    [speclj.core :refer :all]))

(describe "session feature steps"

  #_{:clj-kondo/ignore [:invalid-arity]}
  (around [it]
    (g/reset!)
    (nexus/-with-nested-nexus {:fs (fs/mem-fs)}
      (it))
    (g/reset!))

  (it "fires the config change source when a file is written"
    (let [source (change-source/memory-source "/target/test-state")]
      (change-source/start! source)
      (g/assoc! :mem-fs (nexus/get :fs))
      (g/assoc! :config-change-source source)
      (sut/file-exists-with (str "/target/test-state/.isaac/config/crew/" marigold/captain ".edn") "{:model :llama}")
      (should= (str "crew/" marigold/captain ".edn") (change-source/poll! source 0))
      (change-source/stop! source))))
