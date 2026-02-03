(ns mdm.isaac.migration-embedding-dimension-spec
  (:require [mdm.isaac.migrations.20260202-2017-embedding-dimension :as sut]
            [speclj.core :refer :all]))

(describe "20260202_2017_embedding_dimension migration"

  (it "has an up function"
    (should (fn? sut/up)))

  (it "has a down function"
    (should (fn? sut/down)))

  )
