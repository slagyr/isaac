(ns mdm.isaac.schema-spec
  (:require [speclj.core :refer :all]
            [mdm.isaac.schema :as sut]))

(describe "full schema"

  (it "kinds"
    (should-contain :thought @sut/by-kind)
    (should-contain :friend @sut/by-kind))

)
