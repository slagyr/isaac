(ns mdm.isaac.conversation.ws-spec
  (:require [mdm.isaac.conversation.ws :as sut]
            [mdm.isaac.tool.core :as tool]
            [speclj.core :refer :all]))

(describe "Conversation WebSocket Handlers"

  (before
    (tool/clear!)
    (sut/clear-conversations!))

  (context "get-history!"

    (it "creates a new history atom for unknown user"
      (let [history (sut/get-history! 1)]
        (should (instance? clojure.lang.Atom history))
        (should= [] @history)))

    (it "returns the same atom on subsequent calls for the same user"
      (let [h1 (sut/get-history! 1)
            h2 (sut/get-history! 1)]
        (should (identical? h1 h2))))

    (it "returns different atoms for different users"
      (let [h1 (sut/get-history! 1)
            h2 (sut/get-history! 2)]
        (should-not (identical? h1 h2)))))

  (context "ws-chat"

    (it "requires user authentication"
      (let [result (sut/ws-chat {:params {:text "Hello"}})]
        (should= :fail (:status result))))

    (it "requires text message"
      (let [result (sut/ws-chat {:jwt/payload {:user-id 1}
                                 :params {}})]
        (should= :fail (:status result))))

    (it "returns response from agent"
      (with-redefs [sut/chat-fn (fn [_msgs _tools] {:content "Hello from agent!" :tool-calls nil})]
        (let [result (sut/ws-chat {:jwt/payload {:user-id 1}
                                   :params {:text "Hello Isaac"}})]
          (should= :ok (:status result))
          (should= "Hello from agent!" (-> result :payload :response)))))

    (it "reads JWT from nested :request (WebSocket event structure)"
      (with-redefs [sut/chat-fn (fn [_msgs _tools] {:content "Hi there!" :tool-calls nil})]
        (let [result (sut/ws-chat {:request {:jwt/payload {:user-id 1}}
                                   :params {:text "Hello"}})]
          (should= :ok (:status result))
          (should= "Hi there!" (-> result :payload :response)))))

    (it "maintains conversation history across calls"
      (let [call-count (atom 0)]
        (with-redefs [sut/chat-fn (fn [msgs _tools]
                                    (let [n (swap! call-count inc)]
                                      {:content (str "Response " n) :tool-calls nil}))]
          (sut/ws-chat {:jwt/payload {:user-id 1}
                        :params {:text "First message"}})
          (sut/ws-chat {:jwt/payload {:user-id 1}
                        :params {:text "Second message"}})
          (let [history @(sut/get-history! 1)]
            ;; user1 + assistant1 + user2 + assistant2
            (should= 4 (count history))
            (should= "First message" (:content (first history)))
            (should= "Second message" (:content (nth history 2)))))))

    (it "isolates history between users"
      (with-redefs [sut/chat-fn (fn [_msgs _tools] {:content "OK" :tool-calls nil})]
        (sut/ws-chat {:jwt/payload {:user-id 1}
                      :params {:text "User 1 msg"}})
        (sut/ws-chat {:jwt/payload {:user-id 2}
                      :params {:text "User 2 msg"}})
        (should= 2 (count @(sut/get-history! 1)))
        (should= 2 (count @(sut/get-history! 2)))
        (should= "User 1 msg" (:content (first @(sut/get-history! 1))))
        (should= "User 2 msg" (:content (first @(sut/get-history! 2)))))))

  )
