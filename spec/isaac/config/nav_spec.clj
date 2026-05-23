(ns isaac.config.nav-spec
  (:require
    [isaac.config.nav :as sut]
    [isaac.config.schema :as schema]
    [speclj.core :refer :all]))

(def root schema/root)

(describe "config nav"

  (describe "path->spec"

    (it "returns ok with spec for a known scalar path"
      (let [result (sut/path->spec root "defaults.crew")]
        (should (:ok? result))
        (should= :string (:type (:spec result)))))

    (it "returns ok with spec for a crew entity path"
      (let [result (sut/path->spec root "crew.joe.model")]
        (should (:ok? result))
        (should= :string (:type (:spec result)))))

    (it "returns ok for an effort path (int type)"
      (let [result (sut/path->spec root "crew.joe.effort")]
        (should (:ok? result))
        (should= :int (:type (:spec result)))))

    (it "returns error with failing segment for unknown leaf"
      (let [result (sut/path->spec root "crew.joe.bogus")]
        (should-not (:ok? result))
        (should= "bogus" (:segment result))
        (should (clojure.string/includes? (:error result) "bogus"))))

    (it "returns error with failing segment for unknown root key"
      (let [result (sut/path->spec root "bogus.key")]
        (should-not (:ok? result))
        (should= "bogus" (:segment result))))

    (it "returns ok for a nested compaction path"
      (let [result (sut/path->spec root "crew.joe.compaction.threshold")]
        (should (:ok? result)))))

  (describe "set-value"

    (it "sets a scalar value at a known path"
      (let [result (sut/set-value root {} "defaults.crew" "marvin")]
        (should (:ok? result))
        (should= "marvin" (get-in (:config result) [:defaults :crew]))))

    (it "returns error for unknown path"
      (let [result (sut/set-value root {} "crew.joe.bogus" "x")]
        (should-not (:ok? result))
        (should= "bogus" (:segment result))))

    (it "overwrites existing value"
      (let [base   {:defaults {:crew "old"}}
            result (sut/set-value root base "defaults.crew" "new")]
        (should (:ok? result))
        (should= "new" (get-in (:config result) [:defaults :crew])))))

  (describe "unset-value"

    (it "removes a value at a known path"
      (let [base   {:defaults {:crew "marvin" :model "grover"}}
            result (sut/unset-value root base "defaults.crew")]
        (should (:ok? result))
        (should-be-nil (get-in (:config result) [:defaults :crew]))
        (should= "grover" (get-in (:config result) [:defaults :model]))))

    (it "is idempotent when value is already absent"
      (let [result (sut/unset-value root {} "defaults.crew")]
        (should (:ok? result))))))
