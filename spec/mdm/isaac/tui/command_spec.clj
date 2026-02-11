(ns mdm.isaac.tui.command-spec
  (:require [speclj.core :refer :all]
            [mdm.isaac.tui.command :as cmd]
            [mdm.isaac.tui.core :as core]))

(describe "Command Protocol"

  (describe "ChatCommand"
    (it "matches any non-empty input"
      (should (cmd/matches? cmd/chat-command "hello"))
      (should (cmd/matches? cmd/chat-command "anything")))

    (it "does not match blank input"
      (should-not (cmd/matches? cmd/chat-command ""))
      (should-not (cmd/matches? cmd/chat-command "   ")))

    (it "parses to chat action with text"
      (should= {:action :chat :text "Hello Isaac"}
               (cmd/parse cmd/chat-command "Hello Isaac"))))

  (describe "parse-input (registry lookup)"
    (it "parses any input as chat"
      (should= {:action :chat :text "hello"}
               (cmd/parse-input "hello")))

    (it "parses former slash commands as chat too"
      (should= {:action :chat :text "/goals"}
               (cmd/parse-input "/goals")))

    (it "returns nil for blank input"
      (should-be-nil (cmd/parse-input "   "))))

  (describe "removed commands"
    (it "does not have goals-command"
      (should-be-nil (resolve 'mdm.isaac.tui.command/goals-command)))

    (it "does not have add-command"
      (should-be-nil (resolve 'mdm.isaac.tui.command/add-command)))

    (it "does not have thoughts-command"
      (should-be-nil (resolve 'mdm.isaac.tui.command/thoughts-command)))

    (it "does not have shares-command"
      (should-be-nil (resolve 'mdm.isaac.tui.command/shares-command)))

    (it "does not have search-command"
      (should-be-nil (resolve 'mdm.isaac.tui.command/search-command)))))

(describe "KeyHandler Protocol"

  (describe "QuitHandler"
    (it "matches ctrl+c"
      (should (cmd/key-matches? cmd/quit-handler "ctrl+c")))

    (it "does not match other keys"
      (should-not (cmd/key-matches? cmd/quit-handler "a"))
      (should-not (cmd/key-matches? cmd/quit-handler :tab)))

    (it "returns quit command"
      (let [state (core/init-state)
            [new-state cmd] (cmd/handle-key cmd/quit-handler state "ctrl+c")]
        (should= state new-state)
        (should= :quit cmd))))

  (describe "BackspaceHandler"
    (it "matches :backspace"
      (should (cmd/key-matches? cmd/backspace-handler :backspace)))

    (it "removes last character"
      (let [state (-> (core/init-state) (core/set-input "hello"))
            [new-state cmd] (cmd/handle-key cmd/backspace-handler state :backspace)]
        (should= "hell" (:input new-state))
        (should-be-nil cmd))))

  (describe "EnterHandler"
    (it "matches :enter"
      (should (cmd/key-matches? cmd/enter-handler :enter)))

    (it "adds user message to state and sends (optimistic update)"
      (let [state (-> (core/init-state) (core/set-input "Hello Isaac"))
            [new-state cmd] (cmd/handle-key cmd/enter-handler state :enter)]
        (should= "" (:input new-state))
        (should= {:type :send :text "Hello Isaac"} cmd)
        (should= 1 (count (:messages new-state)))
        (should= {:role :user :content "Hello Isaac"}
                 (first (:messages new-state)))))

    (it "does nothing on empty input"
      (let [state (core/init-state)
            [new-state cmd] (cmd/handle-key cmd/enter-handler state :enter)]
        (should= "" (:input new-state))
        (should-be-nil cmd))))

  (describe "RegularKeyHandler"
    (it "matches single character string"
      (should (cmd/key-matches? cmd/regular-key-handler "a")))

    (it "matches char"
      (should (cmd/key-matches? cmd/regular-key-handler \a)))

    (it "does not match ctrl sequences"
      (should-not (cmd/key-matches? cmd/regular-key-handler "ctrl+c")))

    (it "appends character to input"
      (let [state (core/init-state)
            [new-state cmd] (cmd/handle-key cmd/regular-key-handler state "a")]
        (should= "a" (:input new-state))
        (should-be-nil cmd))))

  (describe "ReconnectHandler"
    (it "triggers reconnect when disconnected with exhausted retries"
      (let [state (-> (core/init-state)
                      (core/set-connection-status :disconnected)
                      (assoc :reconnect-attempts 6))
            [new-state cmd] (cmd/handle-key cmd/reconnect-handler state "R")]
        (should= :reconnect (:type cmd))
        (should= 0 (:reconnect-attempts new-state))))

    (it "treats R as regular input when connected"
      (let [state (-> (core/init-state)
                      (core/set-connection-status :connected))
            [new-state cmd] (cmd/handle-key cmd/reconnect-handler state "R")]
        (should-be-nil cmd)
        (should= "R" (:input new-state)))))

  (describe "removed handlers"
    (it "does not have tab-handler"
      (should-be-nil (resolve 'mdm.isaac.tui.command/tab-handler))))

  (describe "handle-key-input (registry lookup)"
    (it "handles ctrl+c"
      (let [state (core/init-state)
            [_ cmd] (cmd/handle-key-input state "ctrl+c")]
        (should= :quit cmd)))

    (it "handles regular key"
      (let [state (core/init-state)
            [new-state _] (cmd/handle-key-input state "x")]
        (should= "x" (:input new-state))))

    (it "ignores unknown keys"
      (let [state (core/init-state)
            [new-state cmd] (cmd/handle-key-input state :unknown-key)]
        (should= state new-state)
        (should-be-nil cmd)))))
