(ns mdm.isaac.tui.command-spec
  (:require [speclj.core :refer :all]
            [mdm.isaac.tui.command :as cmd]
            [mdm.isaac.tui.core :as core]))

(describe "Command Protocol"

  (describe "GoalsCommand"
    (it "matches /goals"
      (should (cmd/matches? cmd/goals-command "/goals")))

    (it "does not match other input"
      (should-not (cmd/matches? cmd/goals-command "/add foo"))
      (should-not (cmd/matches? cmd/goals-command "hello")))

    (it "parses to goals/list action"
      (should= {:action :goals/list}
               (cmd/parse cmd/goals-command "/goals"))))

  (describe "AddCommand"
    (it "matches /add with content"
      (should (cmd/matches? cmd/add-command "/add Learn macros")))

    (it "does not match /add without content"
      (should-not (cmd/matches? cmd/add-command "/add ")))

    (it "parses to goals/add action with content"
      (should= {:action :goals/add :content "Learn macros"}
               (cmd/parse cmd/add-command "/add Learn macros"))))

  (describe "ThoughtsCommand"
    (it "matches /thoughts"
      (should (cmd/matches? cmd/thoughts-command "/thoughts")))

    (it "parses to thoughts/recent action"
      (should= {:action :thoughts/recent}
               (cmd/parse cmd/thoughts-command "/thoughts"))))

  (describe "SharesCommand"
    (it "matches /shares"
      (should (cmd/matches? cmd/shares-command "/shares")))

    (it "parses to shares/unread action"
      (should= {:action :shares/unread}
               (cmd/parse cmd/shares-command "/shares"))))

  (describe "SearchCommand"
    (it "matches /search with query"
      (should (cmd/matches? cmd/search-command "/search Clojure")))

    (it "does not match /search without query"
      (should-not (cmd/matches? cmd/search-command "/search ")))

    (it "parses to thoughts/search action with query"
      (should= {:action :thoughts/search :query "Clojure"}
               (cmd/parse cmd/search-command "/search Clojure"))))

  (describe "ChatCommand (fallback)"
    (it "matches any non-empty input"
      (should (cmd/matches? cmd/chat-command "hello"))
      (should (cmd/matches? cmd/chat-command "anything")))

    (it "parses to chat action with text"
      (should= {:action :chat :text "Hello Isaac"}
               (cmd/parse cmd/chat-command "Hello Isaac"))))

  (describe "parse-input (registry lookup)"
    (it "uses first matching command"
      (should= {:action :goals/list}
               (cmd/parse-input "/goals")))

    (it "falls through to chat for non-commands"
      (should= {:action :chat :text "hello"}
               (cmd/parse-input "hello")))))

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

  (describe "TabHandler"
    (it "matches :tab"
      (should (cmd/key-matches? cmd/tab-handler :tab)))

    (it "cycles panel"
      (let [state (core/init-state)
            [new-state cmd] (cmd/handle-key cmd/tab-handler state :tab)]
        (should= :thoughts (:active-panel new-state))
        (should-be-nil cmd))))

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

    (it "sends input and clears"
      (let [state (-> (core/init-state) (core/set-input "/goals"))
            [new-state cmd] (cmd/handle-key cmd/enter-handler state :enter)]
        (should= "" (:input new-state))
        (should= {:type :send :text "/goals"} cmd)))

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
