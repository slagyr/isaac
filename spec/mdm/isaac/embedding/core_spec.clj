(ns mdm.isaac.embedding.core-spec
  (:require [mdm.isaac.embedding.core :as sut]
            [speclj.core :refer :all]))


(describe "embedding.core"

  (context "text-embedding multimethod"

    (it "is a multimethod"
      (should (instance? clojure.lang.MultiFn sut/text-embedding)))

    (it "dispatches on the provider argument"
      (should= :test-provider ((.-dispatchFn sut/text-embedding) :test-provider "some text"))))

  )
