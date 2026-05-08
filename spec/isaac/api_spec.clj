(ns isaac.api-spec
  (:require
    [isaac.api :as sut]
    [isaac.comm :as comm-impl]
    [isaac.comm.registry :as registry]
    [isaac.configurator :as configurator-impl]
    [isaac.session.store :as session-store]
    [isaac.session.store.file :as file-store]
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
                (on-turn-end [_ _ _] nil))]
        (should (satisfies? sut/Comm r)))))

  (describe "Reconfigurable"

    (it "re-exports the same named protocol"
      (should= (:name configurator-impl/Reconfigurable) (:name sut/Reconfigurable))
      (should= (set (keys (:sigs configurator-impl/Reconfigurable))) (set (keys (:sigs sut/Reconfigurable)))))

    (it "a type implementing api/Reconfigurable satisfies api/Reconfigurable"
      (let [r (reify sut/Reconfigurable
                (on-startup! [_ _] :started)
                (on-config-change! [_ _ _] :changed))]
        (should (satisfies? sut/Reconfigurable r)))))

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

    (it "create-session! delegates to session store"
      (let [called (atom nil)]
        (with-redefs [file-store/create-store   (fn [state-dir] [:store state-dir])
                      session-store/open-session! (fn [& args] (reset! called (vec args)) {:id "s1"})]
          (sut/create-session! "/sdir" "my-session" {:crew "main"}))
        (should= [[:store "/sdir"] "my-session" {:crew "main"}] @called)))

    (it "get-session delegates to session store"
      (let [called (atom nil)]
        (with-redefs [file-store/create-store (fn [state-dir] [:store state-dir])
                      session-store/get-session (fn [store id] (reset! called [store id]) {:id "s1"})]
          (sut/get-session "/sdir" "my-session"))
        (should= [[:store "/sdir"] "my-session"] @called))))

  )
