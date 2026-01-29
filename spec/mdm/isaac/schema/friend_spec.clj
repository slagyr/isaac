(ns mdm.isaac.schema.friend-spec
  (:require [speclj.core :refer :all]
            [mdm.isaac.schema.friend :as sut]))

(describe "friend schema"

  (it "structure"
    (should= :friend (-> sut/friend :kind :value)))

  (it "has id field"
    (should= :long (-> sut/friend :id :type)))

  (it "has name field"
    (should= :string (-> sut/friend :name :type)))

  (it "has metadata field as map"
    (should= :map (-> sut/friend :metadata :type)))

)
