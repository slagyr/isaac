(ns isaac.comm.slots-lazy-fixture
  "Fixture namespace loaded lazily by isaac.comm.slots/ensure-impl!."
  (:require [isaac.comm.slots :as slots]))

(defmethod slots/create :lazyimpl [_path _slice] ::lazy)
