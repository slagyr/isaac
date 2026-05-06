(ns isaac.api-spec
  (:require
    [isaac.api :as sut]
    [isaac.comm :as comm-impl]
    [isaac.comm.registry :as registry]
    [isaac.drive.turn :as turn-impl]
    [isaac.lifecycle :as lifecycle-impl]
    [isaac.session.storage :as session-impl]
    [speclj.core :refer :all]))



(describe "isaac.api"

  (describe "Comm"

    (it "re-exports the same named protocol"
      (should= (:name comm-impl/Comm) (:name sut/Comm))
      (should= (set (keys (:sigs comm-impl/Comm))) (set (keys (:sigs sut/Comm)))))

    (it "a type implementing api/Comm satisfies api/Comm"
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
        (should (satisfies? sut/Comm r)))))

  (describe "Lifecycle"

    (it "re-exports the same named protocol"
      (should= (:name lifecycle-impl/Lifecycle) (:name sut/Lifecycle))
      (should= (set (keys (:sigs lifecycle-impl/Lifecycle))) (set (keys (:sigs sut/Lifecycle)))))

    (it "a type implementing api/Lifecycle satisfies api/Lifecycle"
      (let [r (reify sut/Lifecycle
                (on-startup! [_ _] :started)
                (on-config-change! [_ _ _] :changed))]
        (should (satisfies? sut/Lifecycle r)))))

  (describe "comm registry delegates"

    (around [it]
      (binding [registry/*registry* (atom (registry/fresh-registry))]
        (it)))

    (it "register-comm! delegates to comm.registry"
      (sut/register-comm! "parrot" identity)
      (should (registry/registered? "parrot")))

    (it "comm-registered? delegates to comm.registry"
      (registry/register-factory! "parrot" identity)
      (should (sut/comm-registered? "parrot"))
      (should-not (sut/comm-registered? "ghost"))))

  (describe "session delegates"

    (it "create-session! re-exports session.storage/create-session!"
      (should= session-impl/create-session! sut/create-session!))

    (it "get-session re-exports session.storage/get-session"
      (should= session-impl/get-session sut/get-session)))

  (describe "turn delegates"

    (it "run-turn! delegates to drive.turn/run-turn!"
      (let [called (atom nil)]
        (with-redefs [turn-impl/run-turn! (fn [& args] (reset! called (vec args)) {})]
          (sut/run-turn! "sdir" "key" "input" {}))
        (should= ["sdir" "key" "input" {}] @called)))))
