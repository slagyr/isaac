(ns isaac.comm.slots-spec
  (:require
    [c3kit.apron.log :as apron-log]
    [isaac.comm.registry :as comm-registry]
    [isaac.comm.slots :as sut]
    [isaac.spec-helper :as helper]
    [speclj.core :refer :all]))

(def made-host (atom nil))
(defn probe-make [host] (reset! made-host host) :probe-instance)

(describe "comm slots"

  (helper/with-captured-logs)

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example]
    (binding [comm-registry/*registry* (atom (comm-registry/fresh-registry))]
      (example)))

  (describe "impl-factory"

    (it "prefers a programmatically registered constructor"
      (comm-registry/register-factory! "parrot" probe-make)
      (should= probe-make (sut/impl-factory {} "parrot")))

    (it "resolves the constructor from the impl's module-index entry"
      (let [host {:module-index {:isaac.comm.probe
                                 {:manifest {:isaac.server/comm
                                             {:probe {:factory 'isaac.comm.slots-spec/probe-make}}}}}}]
        (should= probe-make (sut/impl-factory host :probe))))

    (it "returns nil and logs activation failure for an unknown impl"
      (apron-log/capture-logs
        (should-be-nil (sut/impl-factory {:module-index {}} "ghost"))))

    (it "returns nil and logs when the entry factory does not resolve"
      (apron-log/capture-logs
        (let [host {:module-index {:isaac.comm.broken
                                   {:manifest {:isaac.server/comm
                                               {:broken {:factory 'no.such.ns/make}}}}}}]
          (should-be-nil (sut/impl-factory host :broken))))))

  (describe "registry"

    (it "derives the slot-tree registry from the comm berth declaration"
      (let [index {:isaac.server
                   {:manifest {:berths {:isaac.server/comm
                                        {:config {:path   [:comms]
                                                  :schema {:value-spec {:factory 'isaac.comm.slots/impl-factory}}}}}}}}
            registry (sut/registry index)]
        (should= :slot-tree (:kind registry))
        (should= [:comms] (:path registry))
        (should= sut/impl-factory (:slot-factory registry))))

    (it "falls back to the comm defaults when no declaration is present"
      (let [registry (sut/registry {})]
        (should= [:comms] (:path registry))
        (should= sut/impl-factory (:slot-factory registry))))))
