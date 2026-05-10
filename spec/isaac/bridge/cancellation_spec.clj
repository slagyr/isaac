(ns isaac.bridge.cancellation-spec
  (:require
    [isaac.bridge.cancellation :as sut]
    [isaac.system :as system]
    [speclj.core :refer :all]))

(describe "bridge cancellation"

  #_{:clj-kondo/ignore [:unresolved-symbol]}
  (around [example]
    (sut/clear!)
    (example)
    (sut/clear!))

  (it "cancels an active turn across system bindings"
    (let [called? (atom false)
          turn    (system/with-system {}
                    (sut/begin-turn! "abc"))]
      (system/with-system {}
        (sut/on-cancel! "abc" #(reset! called? true))
        (sut/cancel! "abc")
        (should @called?)
        (should (sut/cancelled? "abc")))
      (system/with-system {}
        (sut/end-turn! "abc" turn)
        (should-not (sut/cancelled? "abc")))))

  (it "applies a pending cancel to the next turn across system bindings"
    (system/with-system {}
      (sut/cancel! "later"))
    (let [turn (system/with-system {}
                 (sut/begin-turn! "later"))]
      (system/with-system {}
        (should (sut/cancelled? "later"))
        (sut/end-turn! "later" turn)
        (should-not (sut/cancelled? "later"))))))
