(ns isaac.api.registry
  (:require [isaac.comm.registry :as impl]))

(def register-name!    impl/register-name!)
(def register-factory! impl/register-factory!)
(def registered?       impl/registered?)
(def factory-for       impl/factory-for)
(def registered-names  impl/registered-names)
