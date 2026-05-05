(ns isaac.api.registry-spec
  (:require
    [isaac.api.registry :as sut]
    [isaac.comm.registry :as impl]
    [speclj.core :refer :all]))

(describe "isaac.api.registry"

  (around [it]
    (binding [impl/*registry* (atom (impl/fresh-registry))]
      (it)))

  (it "register-factory! delegates to comm.registry"
    (sut/register-factory! "parrot" identity)
    (should (impl/registered? "parrot")))

  (it "register-name! delegates to comm.registry"
    (sut/register-name! "parrot")
    (should (impl/registered? "parrot")))

  (it "registered? delegates to comm.registry"
    (impl/register-name! "parrot")
    (should (sut/registered? "parrot"))
    (should-not (sut/registered? "ghost")))

  (it "factory-for delegates to comm.registry"
    (sut/register-factory! "parrot" identity)
    (should= identity (sut/factory-for "parrot")))

  (it "registered-names delegates to comm.registry"
    (sut/register-name! "parrot")
    (should (contains? (sut/registered-names) "parrot"))))
