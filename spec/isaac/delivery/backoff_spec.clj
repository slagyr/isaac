(ns isaac.delivery.backoff-spec
  (:require
    [isaac.delivery.backoff :as sut]
    [speclj.core :refer :all]))

(describe "delivery backoff"

  (it "returns the configured exponential backoff table"
    (should= [1000 5000 30000 120000 600000]
             (mapv sut/delay-ms [1 2 3 4 5])))

  (it "returns nil when attempts exceed the retry table"
    (should-be-nil (sut/delay-ms 6))))
