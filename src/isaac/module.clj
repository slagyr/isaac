(ns isaac.module)

(defprotocol Module
  (on-startup [this])
  (on-shutdown [this]))

(defn module
  ([] (module {}))
  ([{:keys [on-startup on-shutdown]}]
   (reify Module
     (on-startup [this]
       (when on-startup
         (on-startup this)))
     (on-shutdown [this]
       (when on-shutdown
         (on-shutdown this))))))

(defn module? [value]
  (satisfies? Module value))
