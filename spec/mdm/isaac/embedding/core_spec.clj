(ns mdm.isaac.embedding.core-spec
  (:require [mdm.isaac.embedding.core :as sut]
            [speclj.core :refer :all]))


(describe "embedding.core"

  (context "embed multimethod"

    (it "is a multimethod"
      (should (instance? clojure.lang.MultiFn sut/embed)))

    (it "dispatches on the provider argument"
      (should= :test-provider ((.-dispatchFn sut/embed) :test-provider "some text"))))

  )
