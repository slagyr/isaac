(ns isaac.bridge.cancellation-spec
  (:require
    [isaac.bridge.cancellation :as sut]
    [isaac.system :as system]
    [speclj.core :refer :all]))

(defn- with-active-turns [f]
  (system/with-system {:active-turns (atom {})}
    (f)))

(describe "bridge cancellation"

  (around [it]
    (with-active-turns it))

  (it "stores active turns in the system active-turns atom"
    (let [turns* (atom {})]
      (system/with-system {:active-turns turns*}
        (sut/begin-turn! "abc")
        (should-not-be-nil (get @turns* "abc")))))

  (it "removes a turn from the system active-turns atom on end"
    (let [turns* (atom {})]
      (system/with-system {:active-turns turns*}
        (let [turn (sut/begin-turn! "abc")]
          (sut/end-turn! "abc" turn)
          (should-be-nil (get @turns* "abc")))))))
