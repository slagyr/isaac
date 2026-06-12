(ns isaac.session.session-steps-spec
  (:require
    [isaac.config.api :as config]
    [gherclj.core :as g]
    [isaac.config.runtime :as runtime]
    [isaac.fs :as fs]
    [isaac.llm.api.grover :as grover]
    [isaac.marigold :as marigold]
    [isaac.session.session-steps :as sut]
    [isaac.nexus :as nexus]
    [speclj.core :refer [around describe it should should=]]))

(describe "session feature steps"

  #_{:clj-kondo/ignore [:invalid-arity]}
  (around [it]
    (g/reset!)
    (grover/reset-queue!)
    (nexus/-with-nested-nexus {:fs (fs/mem-fs)}
      (it))
    (grover/reset-queue!)
    (g/reset!))

  (it "fires the config change source when a file is written"
    (let [source (runtime/memory-source "/target/test-state")]
      (runtime/start! source)
      (g/assoc! :mem-fs (nexus/get :fs))
      (g/assoc! :config-change-source source)
      (sut/file-exists-with (str "/target/test-state/config/crew/" marigold/captain ".edn") "{:model :llama}")
      (should= (str "crew/" marigold/captain ".edn") (runtime/poll! source 0))
      (runtime/stop! source)))

  (it "does not wait for a Grover gate after a turn already completed"
    (g/assoc! :turn-future (future {:output "done"
                                    :request {:id :request}
                                    :result  {:ok true}}))
    (let [started-at (System/nanoTime)]
      (sut/turn-ends-on-session "bridge")
      (should (< (/ (- (System/nanoTime) started-at) 1000000.0)
                 500.0)))
    (should= {:ok true} (g/get :llm-result)))

  (it "reuses loaded config until a feature fixture changes it"
    (let [loads* (atom 0)
          cfg    {:defaults {:crew "main"}
                  :crew     {"main" {}}
                  :models   {}
                  :providers {}}]
      (g/assoc! :root "/target/test-state")
      (g/assoc! :mem-fs (nexus/get :fs))
      (with-redefs [config/load-config-result (fn [_]
                                                (swap! loads* inc)
                                                {:config cfg})]
        (should= cfg (#'sut/loaded-config))
        (should= cfg (#'sut/loaded-config))
        (should= 1 @loads*)
        (sut/file-exists-with "config/crew/main.edn" "{:model :grover}")
        (should= cfg (#'sut/loaded-config))
        (should= 2 @loads*)))))
