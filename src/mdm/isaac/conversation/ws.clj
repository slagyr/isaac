(ns mdm.isaac.conversation.ws
  "WebSocket handlers for conversations."
  (:require [c3kit.bucket.api :as db]
            [c3kit.wire.apic :as apic]
            [mdm.isaac.conversation.core :as conversation]
            [mdm.isaac.embedding.core :as embedding]
            [mdm.isaac.llm.core :as llm]))

(defn get-or-create-conversation
  "Returns the user's active conversation, creating one if none exists."
  [user-id]
  (or (db/ffind-by :conversation :user-id user-id :status "active")
      (db/tx {:kind       :conversation
              :user-id    user-id
              :status     "active"
              :started-at (java.util.Date.)})))

(defn ws-chat
  "Handles a chat message from the user.
   Expects {:params {:text \"message\"}} and :jwt/payload with :user-id.
   JWT may be at top-level or nested under :request (WebSocket event wrapper)."
  [{:keys [params] :as request}]
  (let [user-id (or (-> request :request :jwt/payload :user-id)
                    (-> request :jwt/payload :user-id))
        text    (:text params)]
    (cond
      (nil? user-id) (apic/fail)
      (empty? text)  (apic/fail)
      :else
      (let [conv   (get-or-create-conversation user-id)
            result (conversation/chat! (:id conv) text
                                       {:llm-fn   llm/chat
                                        :embed-fn embedding/text-embedding})]
        (apic/ok result)))))
