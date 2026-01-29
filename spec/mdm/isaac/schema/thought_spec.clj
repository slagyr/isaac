(ns mdm.isaac.schema.thought-spec
  (:require [speclj.core :refer :all]
            [mdm.isaac.schema.thought :as sut]))

(describe "thought schema"

  (it "structure"
    (should= :thought (-> sut/thought :kind :value)))

  (it "has type field with allowed values"
    (should= :keyword (-> sut/thought :type :type))
    (should= #{:thought :goal :insight :question :share} (-> sut/thought :type :validate)))

  (it "has status field for goal state"
    (should= :keyword (-> sut/thought :status :type))
    (should= #{:active :resolved :abandoned} (-> sut/thought :status :validate)))

  (it "has priority field"
    (should= :long (-> sut/thought :priority :type)))

)
