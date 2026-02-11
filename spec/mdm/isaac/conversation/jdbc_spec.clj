(ns mdm.isaac.conversation.jdbc-spec
  "Integration tests for conversation with PostgreSQL.
   These tests verify that string fields (status, role) work correctly
   with the JDBC backend."
  (:require [c3kit.apron.app :as app]
            [c3kit.bucket.api :as db]
            [mdm.isaac.config :as config]
            [mdm.isaac.init :as init]
            [mdm.isaac.server.main :as main]
            [speclj.core :refer :all]))

(defn unique-email [prefix]
  (str prefix "-" (System/currentTimeMillis) "@example.com"))

(describe "Conversation JDBC Integration"

  (tags :slow)

  (before-all
    (init/install-legend!)
    (app/start! [main/bucket-service]))

  (after-all
    (app/stop! [main/bucket-service]))

  (context "string field serialization"

    (it "stores and retrieves conversation with string status"
      (let [user (db/tx {:kind :user :email (unique-email "jdbc-conv-test")})
            conv (db/tx {:kind       :conversation
                         :user-id    (:id user)
                         :status     "active"
                         :started-at (java.util.Date.)})]
        ;; Verify we can query by string status
        (let [found (db/ffind-by :conversation :user-id (:id user) :status "active")]
          (should-not-be-nil found)
          (should= "active" (:status found))
          (should= (:id conv) (:id found)))
        ;; Clean up
        (db/delete conv)
        (db/delete user)))

    (it "stores and retrieves message without thought_ids"
      (let [user (db/tx {:kind :user :email (unique-email "jdbc-msg-test")})
            conv (db/tx {:kind       :conversation
                         :user-id    (:id user)
                         :status     "active"
                         :started-at (java.util.Date.)})
            msg  (db/tx {:kind            :message
                         :conversation-id (:id conv)
                         :role            "user"
                         :content         "Hello"
                         :created-at      (java.util.Date.)})]
        ;; Verify we can retrieve the message
        (let [found (db/entity :message (:id msg))]
          (should-not-be-nil found)
          (should= "user" (:role found))
          (should= "Hello" (:content found)))
        ;; Clean up
        (db/delete msg)
        (db/delete conv)
        (db/delete user)))

    (it "queries conversation by string status"
      (let [user   (db/tx {:kind :user :email (unique-email "jdbc-query-test")})
            active (db/tx {:kind       :conversation
                           :user-id    (:id user)
                           :status     "active"
                           :started-at (java.util.Date.)})
            closed (db/tx {:kind       :conversation
                           :user-id    (:id user)
                           :status     "closed"
                           :started-at (java.util.Date.)})]
        ;; Query for active only
        (let [found (db/ffind-by :conversation :user-id (:id user) :status "active")]
          (should= (:id active) (:id found)))
        ;; Query for closed only
        (let [found (db/ffind-by :conversation :user-id (:id user) :status "closed")]
          (should= (:id closed) (:id found)))
        ;; Clean up
        (db/delete active)
        (db/delete closed)
        (db/delete user)))))
