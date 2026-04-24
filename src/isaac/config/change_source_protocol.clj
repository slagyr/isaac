(ns isaac.config.change-source-protocol)

(defprotocol ConfigChangeSource
  (-start! [this])
  (-stop! [this])
  (-poll! [this timeout-ms])
  (-notify-path! [this path]))
