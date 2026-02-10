(ns mdm.isaac.conversation.schema
  (:require [c3kit.apron.schema :as s]))

(def conversation-statuses #{:active :closed})
(def message-roles #{:user :isaac})

(def conversation
  {:kind       (s/kind :conversation)
   :id         {:type :long}
   :user-id    {:type :long}
   :started-at {:type :instant}
   :updated-at {:type :instant}
   :status     {:type :keyword :validate conversation-statuses}})

(def message
  {:kind            (s/kind :message)
   :id              {:type :long}
   :conversation-id {:type :long}
   :role            {:type :keyword :validate message-roles}
   :content         {:type :string}
   :created-at      {:type :instant}
   :thought-ids     {:type [:long]}})

(def all [conversation message])
