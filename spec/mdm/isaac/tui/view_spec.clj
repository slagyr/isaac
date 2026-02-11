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

    (it "shows reconnecting status with message"
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

    (it "shows thinking status when goal is active"
      (let [state (-> (core/init-state)
                      (core/set-goals [{:id 1 :content "Learn macros" :status "active"}]))
            status (view/render-status state)]
        (should-contain "Learn macros" status))))

  (describe "render-goals"
    (it "shows 'No goals' when empty"
      (let [state (core/init-state)
            rendered (view/render-goals state)]
        (should-contain "No goals" rendered)))

    (it "renders goals list with status"
      (let [state (-> (core/init-state)
                      (core/set-goals [{:id 1 :content "Learn Clojure" :status "active"}
                                       {:id 2 :content "Build app" :status "resolved"}]))
            rendered (view/render-goals state)]
        (should-contain "Learn Clojure" rendered)
        (should-contain "Build app" rendered)
        (should-contain "active" rendered)))

    (it "shows panel title"
      (let [state (core/init-state)
            rendered (view/render-goals state)]
        (should-contain "Goals" rendered))))

  (describe "render-thoughts"
    (it "shows 'No thoughts' when empty"
      (let [state (core/init-state)
            rendered (view/render-thoughts state)]
        (should-contain "No thoughts" rendered)))

    (it "renders thoughts list"
      (let [state (-> (core/init-state)
                      (core/set-thoughts [{:id 1 :content "A thought about life" :type "thought"}
                                          {:id 2 :content "An insight!" :type "insight"}]))
            rendered (view/render-thoughts state)]
        (should-contain "A thought about life" rendered)
        (should-contain "An insight!" rendered)))

    (it "shows panel title"
      (let [state (core/init-state)
            rendered (view/render-thoughts state)]
        (should-contain "Thoughts" rendered))))

  (describe "render-shares"
    (it "shows 'No shares' when empty"
      (let [state (core/init-state)
            rendered (view/render-shares state)]
        (should-contain "No shares" rendered)))

    (it "renders shares list"
      (let [state (-> (core/init-state)
                      (core/set-shares [{:id 1 :content "I want to tell you something" :type "share"}]))
            rendered (view/render-shares state)]
        (should-contain "I want to tell you something" rendered)))

    (it "shows panel title"
      (let [state (core/init-state)
            rendered (view/render-shares state)]
        (should-contain "Shares" rendered))))

  (describe "render-input"
    (it "shows prompt with empty input"
      (let [state (core/init-state)
            rendered (view/render-input state)]
        (should-contain ">" rendered)))

    (it "shows current input text"
      (let [state (-> (core/init-state)
                      (core/set-input "hello world"))
            rendered (view/render-input state)]
        (should-contain "hello world" rendered))))

  (describe "render-help"
    (it "shows key bindings"
      (let [state (core/init-state)
            rendered (view/render-help state)]
        (should-contain "Tab" rendered)))

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

    (it "shows panel title"
      (let [state (core/init-state)
            rendered (view/render-conversation state)]
        (should-contain "Conversation" rendered)))

    (it "shows most recent messages (limits to 10)"
      (let [messages (mapv (fn [i] {:role :user :content (str "Message " i)}) (range 15))
            state (assoc (core/init-state) :messages messages)
            rendered (view/render-conversation state)]
        ;; Should see recent messages, not the first ones
        (should-contain "Message 14" rendered)
        (should-not-contain "Message 0" rendered))))

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
        (should-contain ">" rendered)))

    (it "includes conversation section when messages exist"
      (let [state (-> (core/init-state)
                      (core/add-message {:role :user :content "Hello"}))
            rendered (view/view state)]
        (should-contain "Conversation" rendered)
        (should-contain "Hello" rendered)))))
