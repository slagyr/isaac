(ns isaac.api.provider
  (:require [isaac.provider :as impl]))

(def register-provider! impl/register!)
(def factory-for        impl/factory-for)
(def registered-apis    impl/registered-apis)
