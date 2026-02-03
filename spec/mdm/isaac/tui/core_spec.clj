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
        (should= "Thinking about \"Learn macros\"" (core/current-thinking state))))))
