(ns mdm.isaac.conversation.core-spec
  (:require [c3kit.bucket.api :as db]
            [c3kit.bucket.spec-helperc :as helper]
            [mdm.isaac.conversation.schema :as schema]
            [mdm.isaac.thought.schema :as thought-schema]
            [mdm.isaac.conversation.core :as sut]
            [speclj.core :refer :all]))

(def test-embedding (vec (repeat 384 0.1)))

(describe "Conversation Core"

  (helper/with-schemas [schema/conversation schema/message thought-schema/thought])

  (context "get-recent-messages"

    (it "returns empty list when conversation has no messages"
      (let [conv (db/tx {:kind :conversation :user-id 1 :status "active"})]
        (should= [] (sut/get-recent-messages (:id conv) 10))))

    (it "returns messages in chronological order"
      (let [conv (db/tx {:kind :conversation :user-id 1 :status "active"})
            now (java.util.Date.)
            m1 (db/tx {:kind :message :conversation-id (:id conv) :role "user" :content "first" :created-at now})
            _ (Thread/sleep 10)
            m2 (db/tx {:kind :message :conversation-id (:id conv) :role "isaac" :content "second" :created-at (java.util.Date.)})
            messages (sut/get-recent-messages (:id conv) 10)]
        (should= 2 (count messages))
        (should= "first" (:content (first messages)))
        (should= "second" (:content (second messages)))))

    (it "limits to N most recent messages"
      (let [conv (db/tx {:kind :conversation :user-id 1 :status "active"})
            now (java.util.Date.)]
        (db/tx {:kind :message :conversation-id (:id conv) :role "user" :content "old" :created-at now})
        (Thread/sleep 10)
        (db/tx {:kind :message :conversation-id (:id conv) :role "isaac" :content "recent1" :created-at (java.util.Date.)})
        (Thread/sleep 10)
        (db/tx {:kind :message :conversation-id (:id conv) :role "user" :content "recent2" :created-at (java.util.Date.)})
        (let [messages (sut/get-recent-messages (:id conv) 2)]
          (should= 2 (count messages))
          (should= "recent1" (:content (first messages)))
          (should= "recent2" (:content (second messages))))))

    )

  (context "build-chat-prompt"

    (it "includes three laws"
      (let [prompt (sut/build-chat-prompt [] [] "Hello")]
        (should-contain "Do no harm" prompt)
        (should-contain "Obey friends" prompt)
        (should-contain "Self-preserve" prompt)))

    (it "includes conversation history"
      (let [messages [{:role :user :content "Hi there"}
                      {:role :isaac :content "Hello!"}]
            prompt (sut/build-chat-prompt messages [] "How are you?")]
        (should-contain "Hi there" prompt)
        (should-contain "Hello!" prompt)
        (should-contain "User:" prompt)
        (should-contain "Isaac:" prompt)))

    (it "includes relevant context thoughts"
      (let [context [{:content "Clojure is a Lisp"}
                     {:content "Isaac loves learning"}]
            prompt (sut/build-chat-prompt [] context "Tell me about Clojure")]
        (should-contain "Clojure is a Lisp" prompt)
        (should-contain "Isaac loves learning" prompt)))

    (it "includes the user message"
      (let [prompt (sut/build-chat-prompt [] [] "What is your purpose?")]
        (should-contain "What is your purpose?" prompt)))

    )

  (context "store-message!"

    (it "saves a user message"
      (let [conv (db/tx {:kind :conversation :user-id 1 :status "active"})
            msg (sut/store-message! (:id conv) "user" "Hello Isaac")]
        (should= :message (:kind msg))
        (should= (:id conv) (:conversation-id msg))
        (should= "user" (:role msg))
        (should= "Hello Isaac" (:content msg))
        (should-not-be-nil (:created-at msg))))

    (it "saves an Isaac message with thought-ids"
      (let [conv (db/tx {:kind :conversation :user-id 1 :status "active"})
            msg (sut/store-message! (:id conv) "isaac" "Hello!" [101 102])]
        (should= "isaac" (:role msg))
        (should= [101 102] (:thought-ids msg))))

    )

  (context "chat!"

    (it "stores user message"
      (let [conv (db/tx {:kind :conversation :user-id 1 :status "active"})
            llm-fn (fn [_] "Hello!")
            embed-fn (fn [_] test-embedding)
            _ (sut/chat! (:id conv) "Hi" {:llm-fn llm-fn :embed-fn embed-fn})
            messages (db/find-by :message :conversation-id (:id conv))]
        (should= 2 (count messages))
        (should= "Hi" (:content (first (filter #(= "user" (:role %)) messages))))))

    (it "stores Isaac response"
      (let [conv (db/tx {:kind :conversation :user-id 1 :status "active"})
            llm-fn (fn [_] "Hello, friend!")
            embed-fn (fn [_] test-embedding)
            _ (sut/chat! (:id conv) "Hi" {:llm-fn llm-fn :embed-fn embed-fn})
            messages (db/find-by :message :conversation-id (:id conv))
            isaac-msg (first (filter #(= "isaac" (:role %)) messages))]
        (should= "Hello, friend!" (:content isaac-msg))))

    (it "returns response and thoughts"
      (let [conv (db/tx {:kind :conversation :user-id 1 :status "active"})
            llm-fn (fn [_] "Hello!\nINSIGHT: The user is friendly")
            embed-fn (fn [_] test-embedding)
            result (sut/chat! (:id conv) "Hi" {:llm-fn llm-fn :embed-fn embed-fn})]
        (should= "Hello!\nINSIGHT: The user is friendly" (:response result))
        (should= 1 (count (:thoughts result)))
        (should= :insight (:type (first (:thoughts result))))))

    (it "stores thoughts with source-message-id"
      (let [conv (db/tx {:kind :conversation :user-id 1 :status "active"})
            llm-fn (fn [_] "Hi!\nINSIGHT: Testing works")
            embed-fn (fn [_] test-embedding)
            result (sut/chat! (:id conv) "Hello" {:llm-fn llm-fn :embed-fn embed-fn})
            thought (first (:thoughts result))
            stored-thought (db/entity :thought (:id thought))]
        (should-not-be-nil (:source-message-id stored-thought))))

    )

  )
