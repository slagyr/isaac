(ns mdm.isaac.user.ws
  (:require [c3kit.wire.apic :as apic]))

(defn ws-fetch-user-data [_request]
  (apic/ok {:placeholder "temporary"})
  )
