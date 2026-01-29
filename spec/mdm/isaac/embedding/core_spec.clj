(ns mdm.isaac.embedding.core-spec
  (:require [mdm.isaac.embedding.core :as sut]
            [mdm.isaac.embedding.djl]
            [mdm.isaac.embedding.ollama]
            [mdm.isaac.spec-helper :refer [with-config]]
            [speclj.core :refer :all]))


(describe "embedding.core"

  (context "text-embedding multimethod"

    (with-config {:embedding {:impl :mock}})

    (it "is a multimethod"
      (should (instance? clojure.lang.MultiFn sut/text-embedding)))

    (it "dispatches on the provider argument"
      (should= :mock ((.-dispatchFn sut/text-embedding) "some text"))))

  (context "dimensions multimethod"

    (it "is a multimethod"
      (should (instance? clojure.lang.MultiFn sut/dimensions)))

    (context "djl"
      (with-config {:embedding {:impl :djl}})

      (it "returns 384"
        (should= 384 (sut/dimensions))))

    (context "ollama"
      (with-config {:embedding {:impl :ollama}})

      (it "returns 768"
        (should= 768 (sut/dimensions)))))

  )
