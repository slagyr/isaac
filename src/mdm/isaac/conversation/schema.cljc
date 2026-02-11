(ns mdm.isaac.conversation.schema
  (:require [c3kit.apron.schema :as s]))

(def conversation-statuses #{"active" "closed"})
(def message-roles #{"user" "isaac"})

(def conversation
  {:kind       (s/kind :conversation)
   :id         {:type :long}
   :user-id    {:type :long :db {:name "user_id"}}
   :started-at {:type :instant :db {:name "started_at"}}
   :updated-at {:type :instant :db {:name "updated_at"}}
   :status     {:type :string :validate conversation-statuses}})

(def message
  {:kind            (s/kind :message)
   :id              {:type :long}
   :conversation-id {:type :long :db {:name "conversation_id"}}
   :role            {:type :string :validate message-roles}
   :content         {:type :string}
   :created-at      {:type :instant :db {:name "created_at"}}
   :thought-ids     {:type [:long] :db {:name "thought_ids"}}})

(def all [conversation message])
