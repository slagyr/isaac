(ns mdm.isaac.schema.full-spec
  (:require [speclj.core :refer :all]
            [mdm.isaac.schema.full :as sut]))

(describe "full schema"

  (it "kinds"
    (should-contain :thought @sut/by-kind))

)
