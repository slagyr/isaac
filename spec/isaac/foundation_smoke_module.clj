(ns isaac.foundation-smoke-module
  "Fixture module-author namespace for foundation facade smoke tests.
   Requires only isaac.foundation plus documented Tier-1 carve-outs."
  (:require
    [isaac.foundation :as foundation]
    [isaac.cli.api :as cli-api]
    [isaac.logger :as logger]))

(defn create-module []
  (foundation/create-module))

(defrecord SmokeRelay [state*]
  foundation/Reconfigurable
  (on-startup! [_ slice]
    (swap! state* assoc :startup slice)
    (logger/info :smoke/startup slice))
  (on-config-change! [_ old-slice new-slice]
    (swap! state* assoc :change [old-slice new-slice])))

(defn relay []
  (->SmokeRelay (atom {})))

(defmethod cli-api/run :smoke [_id _opts]
  (if (foundation/module? (create-module))
    0
    1))