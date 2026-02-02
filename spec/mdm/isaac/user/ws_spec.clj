(ns mdm.isaac.user.ws-spec
  (:require [c3kit.wire.apic :as apic]
            [speclj.core :refer :all]
            [mdm.isaac.user.ws :as sut]))

(describe "User WS"

  (it "ws-fetch-user-data"
    (let [response (sut/ws-fetch-user-data nil)]
      (should= :ok (apic/status response))
      (should= {:placeholder "temporary"} (apic/payload response))))

)
