(ns isaac.api.comm
  (:require
    [isaac.comm :as impl]
    [isaac.comm.registry :as registry]))

(def Comm impl/Comm)

(def register-name!    registry/register-name!)
(def register-factory! registry/register-factory!)
(def registered?       registry/registered?)
(def factory-for       registry/factory-for)
(def registered-names  registry/registered-names)
