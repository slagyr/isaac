(ns mdm.isaac.tui.core-spec
  (:require [speclj.core :refer :all]
            [mdm.isaac.tui.core :as core]))

(describe "Client State"

  (describe "init-state"
    (it "creates initial state with empty goals"
      (let [state (core/init-state)]
        (should= [] (:goals state))))

    (it "creates initial state with empty thoughts"
      (let [state (core/init-state)]
        (should= [] (:thoughts state))))

    (it "creates initial state with empty shares"
      (let [state (core/init-state)]
        (should= [] (:shares state))))

    (it "creates initial state with disconnected status"
      (let [state (core/init-state)]
        (should= :disconnected (:connection-status state))))

    (it "creates initial state with empty input"
      (let [state (core/init-state)]
        (should= "" (:input state))))

    (it "creates initial state with :goals as active panel"
      (let [state (core/init-state)]
        (should= :goals (:active-panel state)))))

  (describe "state updates"
    (it "set-goals replaces goals list"
      (let [state (core/init-state)
            goals [{:id 1 :content "Learn Clojure" :status :active}]
            new-state (core/set-goals state goals)]
        (should= goals (:goals new-state))))

    (it "set-thoughts replaces thoughts list"
      (let [state (core/init-state)
            thoughts [{:id 1 :content "A thought" :type :thought}]
            new-state (core/set-thoughts state thoughts)]
        (should= thoughts (:thoughts new-state))))

    (it "set-shares replaces shares list"
      (let [state (core/init-state)
            shares [{:id 1 :content "A share" :type :share}]
            new-state (core/set-shares state shares)]
        (should= shares (:shares new-state))))

    (it "set-connection-status updates status"
      (let [state (core/init-state)
            new-state (core/set-connection-status state :connected)]
        (should= :connected (:connection-status new-state))))

    (it "set-input updates input text"
      (let [state (core/init-state)
            new-state (core/set-input state "hello")]
        (should= "hello" (:input new-state))))

    (it "append-input adds character to input"
      (let [state (-> (core/init-state)
                      (core/set-input "hel"))
            new-state (core/append-input state "lo")]
        (should= "hello" (:input new-state))))

    (it "backspace-input removes last character"
      (let [state (-> (core/init-state)
                      (core/set-input "hello"))
            new-state (core/backspace-input state)]
        (should= "hell" (:input new-state))))

    (it "backspace-input handles empty input"
      (let [state (core/init-state)
            new-state (core/backspace-input state)]
        (should= "" (:input new-state))))

    (it "clear-input resets input to empty"
      (let [state (-> (core/init-state)
                      (core/set-input "hello"))
            new-state (core/clear-input state)]
        (should= "" (:input new-state))))

    (it "set-active-panel changes active panel"
      (let [state (core/init-state)
            new-state (core/set-active-panel state :thoughts)]
        (should= :thoughts (:active-panel new-state))))

    (it "cycle-panel cycles through panels"
      (let [state (core/init-state)
            state1 (core/cycle-panel state)
            state2 (core/cycle-panel state1)
            state3 (core/cycle-panel state2)]
        (should= :thoughts (:active-panel state1))
        (should= :shares (:active-panel state2))
        (should= :goals (:active-panel state3)))))

  (describe "current-thinking"
    (it "returns nil when no active goals"
      (let [state (core/init-state)]
        (should-be-nil (core/current-thinking state))))

    (it "returns first active goal as thinking status"
      (let [state (-> (core/init-state)
                      (core/set-goals [{:id 1 :content "Learn macros" :status :active}]))]
        (should= "Thinking about \"Learn macros\"" (core/current-thinking state)))))

  (describe "reconnect state management"
    (it "init-state has zero reconnect-attempts"
      (let [state (core/init-state)]
        (should= 0 (:reconnect-attempts state))))

    (it "increment-reconnect-attempts adds one"
      (let [state (core/init-state)
            new-state (core/increment-reconnect-attempts state)]
        (should= 1 (:reconnect-attempts new-state))))

    (it "reset-reconnect-attempts sets to zero"
      (let [state (-> (core/init-state)
                      (core/increment-reconnect-attempts)
                      (core/increment-reconnect-attempts))
            new-state (core/reset-reconnect-attempts state)]
        (should= 0 (:reconnect-attempts new-state))))

    (it "set-connection-status to :reconnecting preserves reconnect-attempts"
      (let [state (-> (core/init-state)
                      (core/increment-reconnect-attempts)
                      (core/set-connection-status :reconnecting))]
        (should= :reconnecting (:connection-status state))
        (should= 1 (:reconnect-attempts state))))

    (it "set-connection-status to :connected resets reconnect-attempts"
      (let [state (-> (core/init-state)
                      (core/increment-reconnect-attempts)
                      (core/increment-reconnect-attempts)
                      (core/set-connection-status :connected))]
        (should= :connected (:connection-status state))
        (should= 0 (:reconnect-attempts state)))))

  (describe "reconnect-delay"
    (it "returns 1000ms for first attempt"
      (should= 1000 (core/reconnect-delay 0)))

    (it "returns 2000ms for second attempt"
      (should= 2000 (core/reconnect-delay 1)))

    (it "returns 4000ms for third attempt"
      (should= 4000 (core/reconnect-delay 2)))

    (it "returns 8000ms for fourth attempt"
      (should= 8000 (core/reconnect-delay 3)))

    (it "returns 16000ms for fifth attempt"
      (should= 16000 (core/reconnect-delay 4)))

    (it "caps at 30000ms for sixth+ attempt"
      (should= 30000 (core/reconnect-delay 5))
      (should= 30000 (core/reconnect-delay 10))))

  (describe "should-retry?"
    (it "returns true when under max attempts"
      (let [state (core/init-state)]
        (should (core/should-retry? state))))

    (it "returns false when at max attempts"
      (let [state (reduce (fn [s _] (core/increment-reconnect-attempts s))
                          (core/init-state)
                          (range 6))]
        (should-not (core/should-retry? state)))))

  (describe "conversation state"
    (it "init-state has empty messages"
      (let [state (core/init-state)]
        (should= [] (:messages state))))

    (it "init-state has nil conversation-id"
      (let [state (core/init-state)]
        (should-be-nil (:conversation-id state))))

    (it "set-conversation-id updates conversation-id"
      (let [state (core/init-state)
            new-state (core/set-conversation-id state 123)]
        (should= 123 (:conversation-id new-state))))

    (it "add-message appends message to messages list"
      (let [state (core/init-state)
            msg {:role :user :content "Hello"}
            new-state (core/add-message state msg)]
        (should= [msg] (:messages new-state))))

    (it "add-message preserves existing messages"
      (let [msg1 {:role :user :content "Hello"}
            msg2 {:role :isaac :content "Hi there!"}
            state (-> (core/init-state)
                      (core/add-message msg1))
            new-state (core/add-message state msg2)]
        (should= [msg1 msg2] (:messages new-state))))

    (it "set-messages replaces all messages"
      (let [state (-> (core/init-state)
                      (core/add-message {:role :user :content "Old"}))
            new-msgs [{:role :user :content "New"}]
            new-state (core/set-messages state new-msgs)]
        (should= new-msgs (:messages new-state))))

    (it "clear-messages empties messages list"
      (let [state (-> (core/init-state)
                      (core/add-message {:role :user :content "Hello"}))
            new-state (core/clear-messages state)]
        (should= [] (:messages new-state))))))
