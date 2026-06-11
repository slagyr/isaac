(ns isaac.configurator
  (:require
    [isaac.config.configurator :as configurator]))

(def Reconfigurable configurator/Reconfigurable)
(def on-config-change! configurator/on-config-change!)
(def on-startup! configurator/on-startup!)
