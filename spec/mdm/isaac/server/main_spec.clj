(ns mdm.isaac.server.main-spec
  (:require [mdm.isaac.server.main :as sut]
            [speclj.core :refer :all]))

(describe "server/main"

  (it "has all-services defined"
    (should (seq sut/all-services)))

  (it "has start-all function"
    (should (fn? sut/start-all)))

  (it "has stop-all function"
    (should (fn? sut/stop-all)))

  (it "has -main entry point"
    (should (fn? sut/-main)))

  )
