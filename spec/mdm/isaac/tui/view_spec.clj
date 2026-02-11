(ns mdm.isaac.tui.view-spec
  (:require [speclj.core :refer :all]
            [clojure.string :as str]
            [mdm.isaac.tui.ansi :as ansi]
            [mdm.isaac.tui.core :as core]
            [mdm.isaac.tui.view :as view]))

(describe "View Rendering"

  (describe "render-status-bar"
    (it "shows Isaac name and connection status"
      (let [state (core/init-state)
            bar (view/render-status-bar state)]
        (should-contain "Isaac" (ansi/strip-ansi bar))
        (should-contain "Disconnected" (ansi/strip-ansi bar))))

    (it "shows connected status in green"
      (let [state (-> (core/init-state)
                      (core/set-connection-status :connected))
            bar (view/render-status-bar state)]
        (should-contain "Connected" (ansi/strip-ansi bar))))

    (it "shows reconnecting status"
      (let [state (-> (core/init-state)
                      (core/set-connection-status :reconnecting))
            bar (view/render-status-bar state)]
        (should-contain "Reconnecting" (ansi/strip-ansi bar))))

    (it "shows press R to retry when retries exhausted"
      (let [state (-> (core/init-state)
                      (assoc :reconnect-attempts 6)
                      (core/set-connection-status :disconnected))
            bar (view/render-status-bar state)]
        (should-contain "Press R" (ansi/strip-ansi bar))))

    (it "shows host:port when server-uri is set"
      (let [state (core/init-state "ws://localhost:8600/ws")
            bar (view/render-status-bar state)]
        (should-contain "localhost:8600" (ansi/strip-ansi bar))))

    (it "is full terminal width"
      (let [state (-> (core/init-state) (core/set-dimensions 60 24))
            bar (view/render-status-bar state)]
        (should= 60 (ansi/visible-length bar)))))

  (describe "render-messages"
    (it "returns empty prompt when no messages"
      (let [state (core/init-state)
            lines (view/render-messages state 10)]
        (should (some #(str/includes? (ansi/strip-ansi %) "Type a message") lines))))

    (it "renders user messages with You: prefix"
      (let [state (-> (core/init-state)
                      (core/add-message {:role :user :content "Hello there"}))
            lines (view/render-messages state 10)]
        (should (some #(str/includes? (ansi/strip-ansi %) "You:") lines))
        (should (some #(str/includes? (ansi/strip-ansi %) "Hello there") lines))))

    (it "renders Isaac messages with Isaac: prefix"
      (let [state (-> (core/init-state)
                      (core/add-message {:role :isaac :content "Hi! How can I help?"}))
            lines (view/render-messages state 10)]
        (should (some #(str/includes? (ansi/strip-ansi %) "Isaac:") lines))
        (should (some #(str/includes? (ansi/strip-ansi %) "How can I help") lines))))

    (it "returns exactly available-rows lines (bottom-aligned)"
      (let [state (core/init-state)
            lines (view/render-messages state 15)]
        (should= 15 (count lines))))

    (it "fills remaining space with blank lines when few messages"
      (let [state (-> (core/init-state)
                      (core/add-message {:role :user :content "Hello"}))
            lines (view/render-messages state 10)]
        (should= 10 (count lines))
        ;; Most lines should be blank (empty strings)
        (should (> (count (filter #(str/blank? (ansi/strip-ansi %)) lines)) 5))))

    (it "truncates to available rows showing most recent messages"
      (let [messages (mapv (fn [i] {:role :user :content (str "Message " i)}) (range 20))
            state (assoc (core/init-state) :messages messages)
            lines (view/render-messages state 5)]
        (should= 5 (count lines))
        ;; Should show recent messages, not old ones
        (should (some #(str/includes? (ansi/strip-ansi %) "Message 19") lines))
        (should-not (some #(str/includes? (ansi/strip-ansi %) "Message 0") lines)))))

  (describe "render-input-line"
    (it "shows prompt with cursor"
      (let [state (core/init-state)
            line (view/render-input-line state)]
        (should-contain ">" (ansi/strip-ansi line))))

    (it "shows current input text"
      (let [state (-> (core/init-state)
                      (core/set-input "hello world"))
            line (view/render-input-line state)]
        (should-contain "hello world" (ansi/strip-ansi line)))))

  (describe "render-help-bar"
    (it "shows quit binding"
      (let [state (core/init-state)
            bar (view/render-help-bar state)]
        (should-contain "Ctrl+C" (ansi/strip-ansi bar))))

    (it "shows R:reconnect when retries exhausted"
      (let [state (-> (core/init-state)
                      (assoc :connection-status :disconnected
                             :reconnect-attempts 6))
            bar (view/render-help-bar state)]
        (should-contain "R:reconnect" (ansi/strip-ansi bar))))

    (it "does not show R:reconnect when connected"
      (let [state (-> (core/init-state)
                      (core/set-connection-status :connected))
            bar (view/render-help-bar state)]
        (should-not-contain "R:reconnect" (ansi/strip-ansi bar)))))

  (describe "render-error"
    (it "returns nil when no error"
      (let [state (core/init-state)]
        (should-be-nil (view/render-error state))))

    (it "renders error with red color"
      (let [state (core/set-error (core/init-state) "Connection failed")
            rendered (view/render-error state)]
        (should-contain "Connection failed" (ansi/strip-ansi rendered)))))

  (describe "view"
    (it "produces exactly height lines"
      (let [state (-> (core/init-state) (core/set-dimensions 80 24))
            output (view/view state)
            lines (str/split output #"\n" -1)]
        (should= 24 (count lines))))

    (it "produces correct line count for different terminal sizes"
      (let [state (-> (core/init-state) (core/set-dimensions 120 40))
            output (view/view state)
            lines (str/split output #"\n" -1)]
        (should= 40 (count lines))))

    (it "has status bar on first line"
      (let [state (-> (core/init-state) (core/set-dimensions 80 24))
            output (view/view state)
            first-line (first (str/split output #"\n" -1))]
        (should-contain "Isaac" (ansi/strip-ansi first-line))))

    (it "has input line near bottom"
      (let [state (-> (core/init-state)
                      (core/set-dimensions 80 24)
                      (core/set-input "test input"))
            output (view/view state)
            lines (str/split output #"\n" -1)]
        ;; Input is second to last line (height-2, 0-indexed)
        (should-contain "test input" (ansi/strip-ansi (nth lines (- 24 2))))))

    (it "has help bar as last line"
      (let [state (-> (core/init-state) (core/set-dimensions 80 24))
            output (view/view state)
            lines (str/split output #"\n" -1)]
        ;; Help is last line (height-1, 0-indexed)
        (should-contain "Ctrl+C" (ansi/strip-ansi (nth lines (- 24 1))))))

    (it "includes error when present"
      (let [state (-> (core/init-state)
                      (core/set-dimensions 80 24)
                      (core/set-error "Something broke"))
            output (view/view state)]
        (should-contain "Something broke" (ansi/strip-ansi output))))

    (it "fills entire screen even with no messages"
      (let [state (-> (core/init-state) (core/set-dimensions 80 24))
            output (view/view state)
            lines (str/split output #"\n" -1)]
        (should= 24 (count lines))))))
