(ns mdm.isaac.conversation.ws
  "WebSocket handlers for conversations.
   Uses the in-memory agent loop with per-user conversation history."
  (:require [c3kit.wire.apic :as apic]
            [mdm.isaac.conversation.agent :as agent]
            [mdm.isaac.llm.core :as llm]))

;; Per-user conversation history: {user-id -> atom<messages>}
(defonce ^:private conversations (atom {}))

(defn clear-conversations!
  "Clear all conversation histories. Useful for testing."
  []
  (reset! conversations {}))

(defn get-history!
  "Get or create the history atom for a user.
   Thread-safe: uses swap! to atomically create history if missing."
  [user-id]
  (-> (swap! conversations
             (fn [convs]
               (if (contains? convs user-id)
                 convs
                 (assoc convs user-id (atom [])))))
      (get user-id)))

(defn chat-fn
  "Default LLM function for chat with tools."
  [messages tools]
  (llm/chat-with-tools messages tools))

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
      (let [history (get-history! user-id)
            result  (agent/chat! text {:llm-fn  chat-fn
                                       :history history})]
        (apic/ok result)))))
