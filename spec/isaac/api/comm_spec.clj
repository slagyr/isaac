(ns isaac.api.comm-spec
  (:require
    [isaac.api.comm :as sut]
    [isaac.comm :as impl]
    [isaac.comm.registry :as registry]
    [speclj.core :refer :all]))

(describe "isaac.api.comm"

  (it "Comm re-exports the same named protocol"
    (should= (:name impl/Comm) (:name sut/Comm))
    (should= (set (keys (:sigs impl/Comm))) (set (keys (:sigs sut/Comm)))))

  (it "a type implementing sut/Comm satisfies sut/Comm"
    (let [r (reify sut/Comm
              (on-turn-start [_ _ _] nil)
              (on-text-chunk [_ _ _] nil)
              (on-tool-call [_ _ _] nil)
              (on-tool-cancel [_ _ _] nil)
              (on-tool-result [_ _ _ _] nil)
              (on-compaction-start [_ _ _] nil)
              (on-compaction-success [_ _ _] nil)
              (on-compaction-failure [_ _ _] nil)
              (on-compaction-disabled [_ _ _] nil)
              (on-turn-end [_ _ _] nil)
              (on-error [_ _ _] nil))]
      (should (satisfies? sut/Comm r))))

  (context "comm registry re-exports"

    (around [it]
      (binding [registry/*registry* (atom (registry/fresh-registry))]
        (it)))

    (it "register-factory! delegates to comm.registry"
      (sut/register-factory! "parrot" identity)
      (should (registry/registered? "parrot")))

    (it "register-name! delegates to comm.registry"
      (sut/register-name! "parrot")
      (should (registry/registered? "parrot")))

    (it "registered? delegates to comm.registry"
      (registry/register-name! "parrot")
      (should (sut/registered? "parrot"))
      (should-not (sut/registered? "ghost")))

    (it "factory-for delegates to comm.registry"
      (sut/register-factory! "parrot" identity)
      (should= identity (sut/factory-for "parrot")))

    (it "registered-names delegates to comm.registry"
      (sut/register-name! "parrot")
      (should (contains? (sut/registered-names) "parrot")))))
