(ns mdm.isaac.setting.schema
  (:require [c3kit.apron.schema :as s]))

(def config
  {:kind  (s/kind :config)
   :id    {:type :long}
   :key   {:type :keyword}
   :value {:type :string}})
