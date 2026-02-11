(ns mdm.isaac.tui.core-spec
  (:require [speclj.core :refer :all]
            [mdm.isaac.tui.core :as core]))

(describe "Client State"

  (describe "init-state"
    (it "creates initial state with empty messages"
      (let [state (core/init-state)]
        (should= [] (:messages state))))

    (it "creates initial state with nil conversation-id"
      (let [state (core/init-state)]
        (should-be-nil (:conversation-id state))))

    (it "creates initial state with disconnected status"
      (let [state (core/init-state)]
        (should= :disconnected (:connection-status state))))

    (it "creates initial state with empty input"
      (let [state (core/init-state)]
        (should= "" (:input state))))

    (it "creates initial state with server-uri"
      (let [state (core/init-state "ws://localhost:8600/ws")]
        (should= "ws://localhost:8600/ws" (:server-uri state))))

    (it "does not include goals in state"
      (let [state (core/init-state)]
        (should-not-contain :goals state)))

    (it "does not include thoughts in state"
      (let [state (core/init-state)]
        (should-not-contain :thoughts state)))

    (it "does not include shares in state"
      (let [state (core/init-state)]
        (should-not-contain :shares state)))

    (it "does not include active-panel in state"
      (let [state (core/init-state)]
        (should-not-contain :active-panel state)))

    (it "creates initial state with default terminal dimensions"
      (let [state (core/init-state)]
        (should= 80 (:width state))
        (should= 24 (:height state)))))

  (describe "terminal dimensions"
    (it "set-dimensions updates width and height"
      (let [state (core/init-state)
            new-state (core/set-dimensions state 120 40)]
        (should= 120 (:width new-state))
        (should= 40 (:height new-state)))))

  (describe "input state"
    (it "set-input updates input text"
      (let [state (core/init-state)
            new-state (core/set-input state "hello")]
        (should= "hello" (:input new-state))))

    (it "append-input adds text to input"
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
        (should= "" (:input new-state)))))

  (describe "connection state"
    (it "set-connection-status updates status"
      (let [state (core/init-state)
            new-state (core/set-connection-status state :connected)]
        (should= :connected (:connection-status new-state))))

    (it "set-connection-status to :connected resets reconnect-attempts"
      (let [state (-> (core/init-state)
                      (core/increment-reconnect-attempts)
                      (core/increment-reconnect-attempts)
                      (core/set-connection-status :connected))]
        (should= :connected (:connection-status state))
        (should= 0 (:reconnect-attempts state))))

    (it "set-connection-status to :reconnecting preserves reconnect-attempts"
      (let [state (-> (core/init-state)
                      (core/increment-reconnect-attempts)
                      (core/set-connection-status :reconnecting))]
        (should= :reconnecting (:connection-status state))
        (should= 1 (:reconnect-attempts state)))))

  (describe "error state"
    (it "set-error sets error message"
      (let [state (core/init-state)
            new-state (core/set-error state "Something broke")]
        (should= "Something broke" (:error new-state))))

    (it "clear-error removes error"
      (let [state (-> (core/init-state)
                      (core/set-error "Something broke"))
            new-state (core/clear-error state)]
        (should-be-nil (:error new-state)))))

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

    (it "reconnect-delay returns 1000ms for first attempt"
      (should= 1000 (core/reconnect-delay 0)))

    (it "reconnect-delay returns 2000ms for second attempt"
      (should= 2000 (core/reconnect-delay 1)))

    (it "reconnect-delay returns 4000ms for third attempt"
      (should= 4000 (core/reconnect-delay 2)))

    (it "reconnect-delay caps at 30000ms"
      (should= 30000 (core/reconnect-delay 5))
      (should= 30000 (core/reconnect-delay 10)))

    (it "should-retry? returns true when under max attempts"
      (let [state (core/init-state)]
        (should (core/should-retry? state))))

    (it "should-retry? returns false when at max attempts"
      (let [state (reduce (fn [s _] (core/increment-reconnect-attempts s))
                          (core/init-state)
                          (range 6))]
        (should-not (core/should-retry? state)))))

  (describe "conversation state"
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
