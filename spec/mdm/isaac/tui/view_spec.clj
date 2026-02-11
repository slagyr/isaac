(ns mdm.isaac.tui.view-spec
  (:require [speclj.core :refer :all]
            [clojure.string :as str]
            [mdm.isaac.tui.core :as core]
            [mdm.isaac.tui.view :as view]))

(describe "View Rendering"

  (describe "render-status"
    (it "shows disconnected status"
      (let [state (core/init-state)
            status (view/render-status state)]
        (should-contain "Disconnected" status)))

    (it "shows connected status"
      (let [state (-> (core/init-state)
                      (core/set-connection-status :connected))
            status (view/render-status state)]
        (should-contain "Connected" status)))

    (it "shows reconnecting status"
      (let [state (-> (core/init-state)
                      (core/set-connection-status :reconnecting))
            status (view/render-status state)]
        (should-contain "Reconnecting" status)))

    (it "shows press R to retry when disconnected after max attempts"
      (let [state (-> (core/init-state)
                      (core/increment-reconnect-attempts)
                      (core/increment-reconnect-attempts)
                      (core/increment-reconnect-attempts)
                      (core/increment-reconnect-attempts)
                      (core/increment-reconnect-attempts)
                      (core/increment-reconnect-attempts)
                      (core/set-connection-status :disconnected))
            status (view/render-status state)]
        (should-contain "Press R to retry" status)))

    (it "shows host:port from server-uri"
      (let [state (core/init-state "ws://localhost:8600/ws")
            status (view/render-status state)]
        (should-contain "localhost:8600" status)))

    (it "does not show thinking status (removed with goals)"
      (let [state (core/init-state)
            status (view/render-status state)]
        (should-not-contain "Thinking" status))))

  (describe "render-conversation"
    (it "shows help text when empty"
      (let [state (core/init-state)
            rendered (view/render-conversation state)]
        (should-contain "Type a message" rendered)))

    (it "renders user messages with 'You:' prefix"
      (let [state (-> (core/init-state)
                      (core/add-message {:role :user :content "Hello there"}))
            rendered (view/render-conversation state)]
        (should-contain "You:" rendered)
        (should-contain "Hello there" rendered)))

    (it "renders Isaac messages with 'Isaac:' prefix"
      (let [state (-> (core/init-state)
                      (core/add-message {:role :isaac :content "Hi! How can I help?"}))
            rendered (view/render-conversation state)]
        (should-contain "Isaac:" rendered)
        (should-contain "How can I help" rendered)))

    (it "shows most recent messages (limits to 10)"
      (let [messages (mapv (fn [i] {:role :user :content (str "Message " i)}) (range 15))
            state (assoc (core/init-state) :messages messages)
            rendered (view/render-conversation state)]
        (should-contain "Message 14" rendered)
        (should-not-contain "Message 0" rendered)))

    (it "shows count of hidden earlier messages"
      (let [messages (mapv (fn [i] {:role :user :content (str "Message " i)}) (range 15))
            state (assoc (core/init-state) :messages messages)
            rendered (view/render-conversation state)]
        (should-contain "5 earlier messages" rendered))))

  (describe "render-input"
    (it "shows prompt with cursor"
      (let [state (core/init-state)
            rendered (view/render-input state)]
        (should= "> _" rendered)))

    (it "shows current input text with cursor"
      (let [state (-> (core/init-state)
                      (core/set-input "hello world"))
            rendered (view/render-input state)]
        (should= "> hello world_" rendered))))

  (describe "render-help"
    (it "shows quit binding"
      (let [state (core/init-state)
            rendered (view/render-help state)]
        (should-contain "Ctrl+C" rendered)))

    (it "does not show Tab or panel commands"
      (let [state (core/init-state)
            rendered (view/render-help state)]
        (should-not-contain "Tab" rendered)
        (should-not-contain "/goals" rendered)
        (should-not-contain "/thoughts" rendered)
        (should-not-contain "/shares" rendered)))

    (it "shows R:reconnect when disconnected with exhausted retries"
      (let [state (-> (core/init-state)
                      (assoc :connection-status :disconnected
                             :reconnect-attempts 6))
            rendered (view/render-help state)]
        (should-contain "R:reconnect" rendered)))

    (it "does not show R:reconnect when connected"
      (let [state (-> (core/init-state)
                      (core/set-connection-status :connected))
            rendered (view/render-help state)]
        (should-not-contain "R:reconnect" rendered))))

  (describe "render-error"
    (it "returns nil when no error"
      (let [state (core/init-state)]
        (should-be-nil (view/render-error state))))

    (it "renders error message"
      (let [state (core/set-error (core/init-state) "Connection failed")
            rendered (view/render-error state)]
        (should-contain "ERROR" rendered)
        (should-contain "Connection failed" rendered))))

  (describe "view"
    (it "returns non-empty string"
      (let [state (core/init-state)
            rendered (view/view state)]
        (should (string? rendered))
        (should (pos? (count rendered)))))

    (it "includes status section"
      (let [state (core/init-state)
            rendered (view/view state)]
        (should-contain "Isaac" rendered)))

    (it "includes input section"
      (let [state (core/init-state)
            rendered (view/view state)]
        (should-contain "> " rendered)))

    (it "does not include goals panel"
      (let [state (core/init-state)
            rendered (view/view state)]
        (should-not-contain "Goals" rendered)))

    (it "does not include thoughts panel"
      (let [state (core/init-state)
            rendered (view/view state)]
        (should-not-contain "Thoughts" rendered)))

    (it "does not include shares panel"
      (let [state (core/init-state)
            rendered (view/view state)]
        (should-not-contain "Shares" rendered)))

    (it "includes conversation when messages exist"
      (let [state (-> (core/init-state)
                      (core/add-message {:role :user :content "Hello"}))
            rendered (view/view state)]
        (should-contain "Hello" rendered)))))
