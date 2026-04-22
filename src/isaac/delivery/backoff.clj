(ns isaac.delivery.backoff)

(def ^:private delays-ms
  {1 1000
   2 5000
   3 30000
   4 120000
   5 600000})

(defn delay-ms [attempts]
  (get delays-ms attempts))
