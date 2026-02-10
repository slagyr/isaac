(ns mdm.isaac.setting.schema-spec
  (:require [speclj.core #?(:clj :refer :cljs :refer-macros) [describe it should=]]
            [mdm.isaac.setting.schema :as sut]))

(describe "setting schema"

  (it "has kind :config"
    (should= :config (-> sut/config :kind :value)))

  (it "has id field"
    (should= :long (-> sut/config :id :type)))

  (it "has key field as keyword"
    (should= :keyword (-> sut/config :key :type)))

  (it "has value field as string"
    (should= :string (-> sut/config :value :type)))

  )
