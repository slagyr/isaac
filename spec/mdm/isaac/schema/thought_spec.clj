(ns mdm.isaac.schema.thought-spec
  (:require [speclj.core :refer :all]
            [mdm.isaac.schema.thought :as sut]))

(describe "thought schema"

  (it "structure"
    (should= :thought (-> sut/thought :kind :value)))

)
