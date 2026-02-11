(ns mdm.isaac.conversation.schema-spec
  (:require [speclj.core :refer :all]
            [mdm.isaac.conversation.schema :as sut]))

(describe "conversation schema"

  (it "has kind :conversation"
    (should= :conversation (-> sut/conversation :kind :value)))

  (it "has id field"
    (should= :long (-> sut/conversation :id :type)))

  (it "has user-id field"
    (should= :long (-> sut/conversation :user-id :type)))

  (it "has started-at as instant"
    (should= :instant (-> sut/conversation :started-at :type)))

  (it "has updated-at as instant"
    (should= :instant (-> sut/conversation :updated-at :type)))

  (it "has status field with string type"
    (should= :string (-> sut/conversation :status :type)))

  (it "validates status to allowed values"
    (should= #{"active" "closed"} (-> sut/conversation :status :validate)))

  )

(describe "message schema"

  (it "has kind :message"
    (should= :message (-> sut/message :kind :value)))

  (it "has id field"
    (should= :long (-> sut/message :id :type)))

  (it "has conversation-id field"
    (should= :long (-> sut/message :conversation-id :type)))

  (it "has role field with string type"
    (should= :string (-> sut/message :role :type)))

  (it "validates role to user or isaac"
    (should= #{"user" "isaac"} (-> sut/message :role :validate)))

  (it "has content as string"
    (should= :string (-> sut/message :content :type)))

  (it "has created-at as instant"
    (should= :instant (-> sut/message :created-at :type)))

  (it "has thought-ids as vector of longs"
    (should= [:long] (-> sut/message :thought-ids :type)))

  )
