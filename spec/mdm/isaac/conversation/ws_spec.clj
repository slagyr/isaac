(ns mdm.isaac.conversation.ws-spec
  (:require [c3kit.bucket.api :as db]
            [c3kit.bucket.spec-helperc :as helper]
            [mdm.isaac.conversation.schema :as schema.conversation]
            [mdm.isaac.conversation.ws :as sut]
            [mdm.isaac.embedding.core :as embedding]
            [mdm.isaac.llm.core :as llm]
            [mdm.isaac.spec-helper :refer [with-config]]
            [mdm.isaac.thought.schema :as schema.thought]
            [mdm.isaac.user.schema :as schema.user]
            [speclj.core :refer :all]))

(def test-embedding (vec (repeat 384 0.1)))

;; Mock implementations for tests
(defmethod embedding/text-embedding :mock [_text] test-embedding)
(defmethod embedding/dimensions :mock [] 384)
(defmethod llm/chat :mock [_prompt] "Hello! I'm Isaac.\nINSIGHT: Testing works well.")

(describe "Conversation WebSocket Handlers"

  (helper/with-schemas [schema.user/user
                        schema.conversation/conversation
                        schema.conversation/message
                        schema.thought/thought])

  (with-config {:embedding {:impl :mock} :llm {:impl :mock}})

  (with user (db/tx {:kind :user :email "test@example.com"}))

  (context "ws-chat"

    (it "requires user authentication"
      (let [result (sut/ws-chat {:params {:text "Hello"}})]
        (should= :fail (:status result))))

    (it "requires text message"
      (let [result (sut/ws-chat {:jwt/payload {:user-id (:id @user)}
                                 :params {}})]
        (should= :fail (:status result))))

    (it "returns response with chat message"
      (let [result (sut/ws-chat {:jwt/payload {:user-id (:id @user)}
                                 :params {:text "Hello Isaac"}})]
        (should= :ok (:status result))
        (should-contain "Isaac" (-> result :payload :response))))

    (it "creates a conversation for new user"
      (should= 0 (count (db/find :conversation)))
      (sut/ws-chat {:jwt/payload {:user-id (:id @user)}
                    :params {:text "Hello"}})
      (should= 1 (count (db/find :conversation)))
      (should= (:id @user) (-> (db/find :conversation) first :user-id)))

    (it "reuses existing active conversation"
      (let [conv (db/tx {:kind :conversation
                         :user-id (:id @user)
                         :status :active
                         :started-at (java.util.Date.)})]
        (sut/ws-chat {:jwt/payload {:user-id (:id @user)}
                      :params {:text "Hello"}})
        (should= 1 (count (db/find :conversation)))
        (should= (:id conv) (-> (db/find :conversation) first :id))))

    (it "stores messages in conversation"
      (sut/ws-chat {:jwt/payload {:user-id (:id @user)}
                    :params {:text "Hello Isaac"}})
      (let [messages (db/find :message)]
        (should= 2 (count messages))  ;; user + isaac
        (should (some #(= :user (:role %)) messages))
        (should (some #(= :isaac (:role %)) messages))))

    (it "returns thoughts extracted from response"
      (let [result (sut/ws-chat {:jwt/payload {:user-id (:id @user)}
                                 :params {:text "Hello"}})]
        (should= :ok (:status result))
        (should (seq (-> result :payload :thoughts)))
        (should= :insight (-> result :payload :thoughts first :type)))))

  (context "get-or-create-conversation"

    (it "creates new conversation for user without one"
      (let [conv (sut/get-or-create-conversation (:id @user))]
        (should= :conversation (:kind conv))
        (should= (:id @user) (:user-id conv))
        (should= :active (:status conv))))

    (it "returns existing active conversation"
      (let [existing (db/tx {:kind :conversation
                             :user-id (:id @user)
                             :status :active
                             :started-at (java.util.Date.)})
            found (sut/get-or-create-conversation (:id @user))]
        (should= (:id existing) (:id found))))

    (it "creates new conversation if only closed ones exist"
      (db/tx {:kind :conversation
              :user-id (:id @user)
              :status :closed
              :started-at (java.util.Date.)})
      (let [conv (sut/get-or-create-conversation (:id @user))]
        (should= :active (:status conv))
        (should= 2 (count (db/find :conversation)))))))
