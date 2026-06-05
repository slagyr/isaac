(ns isaac.comm.registry-spec
  (:require
    [isaac.comm.registry :as sut]
    [speclj.core :refer :all]))

(describe "comm registry"

  (around [it]
    (binding [sut/*registry* (atom (sut/fresh-registry))]
      (it)))

  (it "registers and returns a factory"
    (let [factory (fn [_] ::instance)]
      (sut/register-factory! "telly" factory)
      (should= factory (sut/factory-for :telly))))

  (it "installs a comm via the berth's per-entry factory"
    ;; Phase 8 (isaac-qqgv): comm registration moved from the legacy
    ;; module-loader handler into the :isaac.server/comm berth's
    ;; per-entry factory.
    (sut/register-comm-entry! [:telly {:factory 'isaac.comm.cli/make}])
    (should-not-be-nil (sut/factory-for "telly"))))
