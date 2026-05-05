(ns isaac.api.turn-spec
  (:require
    [isaac.api.turn :as sut]
    [isaac.drive.turn :as impl]
    [speclj.core :refer :all]))

(describe "isaac.api.turn"

  (it "run-turn! re-exports isaac.drive.turn/run-turn!"
    (should= impl/run-turn! sut/run-turn!)))
