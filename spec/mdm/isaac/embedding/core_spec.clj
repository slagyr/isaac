(ns mdm.isaac.embedding.core-spec
  (:require [mdm.isaac.embedding.core :as sut]
            [mdm.isaac.spec-helper :refer [with-config]]
            [speclj.core :refer :all]))


(describe "embedding.core"

  (context "text-embedding multimethod"

    (with-config {:embedding {:impl :mock}})

    (it "is a multimethod"
      (should (instance? clojure.lang.MultiFn sut/text-embedding)))

    (it "dispatches on the provider argument"
      (should= :mock ((.-dispatchFn sut/text-embedding) "some text"))))

  )
