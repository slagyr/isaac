(ns mdm.isaac.embedding.core-spec
  (:require [mdm.isaac.config :as config]
            [mdm.isaac.embedding.core :as sut]
            [speclj.core :refer :all]))


(describe "embedding.core"

  (context "text-embedding multimethod"

    (redefs-around [config/active (merge config/active {:embedding {:impl :mock}})])
    ;; TODO (isaac-gjv) - MDM: Want: `(with-config {:embedding {:impl :mock}})`.  Create a macro in mdm.isaac.spec-helper to allow this.  Use it in all other cases where we redefine config/active in specs.

    (it "is a multimethod"
      (should (instance? clojure.lang.MultiFn sut/text-embedding)))

    (it "dispatches on the provider argument"
      (should= :mock ((.-dispatchFn sut/text-embedding) "some text"))))

  )
