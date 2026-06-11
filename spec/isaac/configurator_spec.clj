(ns isaac.configurator-spec
  (:require
    [isaac.configurator :as sut]
    [speclj.core :refer :all]))

(deftype LegacyReconfigurable [state]
  sut/Reconfigurable
  (sut/on-startup! [_ slice]
    (swap! state assoc :startup slice))
  (sut/on-config-change! [_ old-slice new-slice]
    (swap! state assoc :change [old-slice new-slice])))

(describe "isaac.configurator compatibility"
  (it "supports implementing the legacy Reconfigurable protocol"
    (let [state    (atom {})
          instance (->LegacyReconfigurable state)]
      (sut/on-startup! instance {:status :started})
      (sut/on-config-change! instance {:status :started} {:status :changed})
      (should= {:startup {:status :started}
                :change  [{:status :started} {:status :changed}]}
               @state))))
