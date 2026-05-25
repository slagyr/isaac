(ns isaac.session.session-steps-spec
  (:require
    [gherclj.core :as g]
    [isaac.config.api :as config]
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
    (let [source (config/memory-source "/target/test-state")]
      (config/start! source)
      (g/assoc! :mem-fs (nexus/get :fs))
      (g/assoc! :config-change-source source)
      (sut/file-exists-with (str "/target/test-state/config/crew/" marigold/captain ".edn") "{:model :llama}")
      (should= (str "crew/" marigold/captain ".edn") (config/poll! source 0))
      (config/stop! source))))
