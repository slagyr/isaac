(ns mdm.isaac.ui-spec
  (:require [mdm.isaac.ui :as ui]
            [speclj.core :refer :all]))

(describe "UI Protocol"

  (describe "ConsoleUI"
    (it "inform prints message to stdout"
      (let [output (with-out-str (ui/inform (ui/->ConsoleUI) "Hello"))]
        (should= "Hello\n" output)))

    (it "warn prints message with prefix"
      (let [output (with-out-str (ui/warn (ui/->ConsoleUI) "Warning!"))]
        (should= "[WARN] Warning!\n" output)))

    (it "error prints message with prefix"
      (let [output (with-out-str (ui/error (ui/->ConsoleUI) "Error!"))]
        (should= "[ERROR] Error!\n" output))))

  (describe "MockUI"
    (it "inform stores message in atom"
      (let [messages (atom [])
            mock-ui (ui/->MockUI messages)]
        (ui/inform mock-ui "Test message")
        (should= [{:type :info :msg "Test message"}] @messages)))

    (it "warn stores message with type"
      (let [messages (atom [])
            mock-ui (ui/->MockUI messages)]
        (ui/warn mock-ui "Warning message")
        (should= [{:type :warn :msg "Warning message"}] @messages)))

    (it "error stores message with type"
      (let [messages (atom [])
            mock-ui (ui/->MockUI messages)]
        (ui/error mock-ui "Error message")
        (should= [{:type :error :msg "Error message"}] @messages)))

    (it "accumulates multiple messages"
      (let [messages (atom [])
            mock-ui (ui/->MockUI messages)]
        (ui/inform mock-ui "First")
        (ui/warn mock-ui "Second")
        (ui/error mock-ui "Third")
        (should= 3 (count @messages))
        (should= :info (:type (first @messages)))
        (should= :warn (:type (second @messages)))
        (should= :error (:type (last @messages)))))))
