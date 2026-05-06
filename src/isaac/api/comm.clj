(ns isaac.api.comm
  (:require
    [isaac.comm :as impl]
    [isaac.comm.registry :as registry]))

(def Comm impl/Comm)

(def register-comm! registry/register-factory!)
(def comm-registered? registry/registered?)
