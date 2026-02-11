(ns mdm.isaac.conversation.schema
  (:require [c3kit.apron.schema :as s]))

(def conversation-statuses #{:active :closed})
(def message-roles #{:user :isaac})

(defn- kw->str [v] (if (keyword? v) (name v) v))
(defn- str->kw [v] (if (string? v) (keyword v) v))

(def conversation
  {:kind       (s/kind :conversation)
   :id         {:type :long}
   :user-id    {:type :long :db {:name "user_id"}}
   :started-at {:type :instant :db {:name "started_at"}}
   :updated-at {:type :instant :db {:name "updated_at"}}
   :status     {:type     :keyword
                :validate conversation-statuses
                :db       {:name "status" :coerce kw->str}
                :coerce   str->kw}})

(def message
  {:kind            (s/kind :message)
   :id              {:type :long}
   :conversation-id {:type :long :db {:name "conversation_id"}}
   :role            {:type     :keyword
                     :validate message-roles
                     :db       {:name "role" :coerce kw->str}
                     :coerce   str->kw}
   :content         {:type :string}
   :created-at      {:type :instant :db {:name "created_at"}}
   :thought-ids     {:type [:long] :db {:name "thought_ids"}}})

(def all [conversation message])
