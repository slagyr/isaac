(ns mdm.isaac.tui.view-spec
  (:require [speclj.core :refer :all]
            [clojure.string :as str]
            [mdm.isaac.tui.ansi :as ansi]
            [mdm.isaac.tui.core :as core]
            [mdm.isaac.tui.view :as view]))

(defn- extract-row
  "Extracts the content rendered at a specific row (1-based) from view output.
   View output uses ESC[row;1H<content>ESC[K positioning."
  [output row]
  (let [pattern (re-pattern (str "\\u001b\\[" row ";1H(.*?)(?:\\u001b\\[K)"))]
    (when-let [match (re-find pattern output)]
      (second match))))

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
        (should (> (count (filter #(str/blank? (ansi/strip-ansi %)) lines)) 5))))

    (it "truncates to available rows showing most recent messages"
      (let [messages (mapv (fn [i] {:role :user :content (str "Message " i)}) (range 20))
            state (assoc (core/init-state) :messages messages)
            lines (view/render-messages state 5)]
        (should= 5 (count lines))
        (should (some #(str/includes? (ansi/strip-ansi %) "Message 19") lines))
        (should-not (some #(str/includes? (ansi/strip-ansi %) "Message 0") lines))))

    (it "shows older messages when scroll-offset > 0"
      (let [messages (mapv (fn [i] {:role :user :content (str "Message " i)}) (range 20))
            state (-> (core/init-state)
                      (assoc :messages messages :scroll-offset 5))
            lines (view/render-messages state 5)]
        (should= 5 (count lines))
        ;; With offset 5 from the end, should show messages 10-14 (not 15-19)
        (should (some #(str/includes? (ansi/strip-ansi %) "Message 14") lines))
        (should-not (some #(str/includes? (ansi/strip-ansi %) "Message 19") lines))))

    (it "clamps scroll-offset to not go before first message"
      (let [messages (mapv (fn [i] {:role :user :content (str "Message " i)}) (range 8))
            state (-> (core/init-state)
                      (assoc :messages messages :scroll-offset 100))
            lines (view/render-messages state 5)]
        (should= 5 (count lines))
        ;; Should show earliest messages (clamped)
        (should (some #(str/includes? (ansi/strip-ansi %) "Message 0") lines))))

    (it "shows scroll indicator when scrolled up"
      (let [messages (mapv (fn [i] {:role :user :content (str "Message " i)}) (range 20))
            state (-> (core/init-state)
                      (assoc :messages messages :scroll-offset 5))]
        ;; The help bar should indicate there are more messages below
        (should-contain "more below" (ansi/strip-ansi (view/render-help-bar state)))))

    (it "does not show scroll indicator when at bottom"
      (let [messages (mapv (fn [i] {:role :user :content (str "Message " i)}) (range 20))
            state (-> (core/init-state)
                      (assoc :messages messages :scroll-offset 0))]
        (should-not-contain "more below" (ansi/strip-ansi (view/render-help-bar state))))))

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
    (it "has status bar on row 1"
      (let [state (-> (core/init-state) (core/set-dimensions 80 24))
            output (view/view state)
            row1 (extract-row output 1)]
        (should-not-be-nil row1)
        (should-contain "Isaac" (ansi/strip-ansi row1))))

    (it "has separator on row 2"
      (let [state (-> (core/init-state) (core/set-dimensions 80 24))
            output (view/view state)
            row2 (extract-row output 2)]
        (should-not-be-nil row2)
        (should-contain "---" (ansi/strip-ansi row2))))

    (it "has input line on row height-1"
      (let [state (-> (core/init-state)
                      (core/set-dimensions 80 24)
                      (core/set-input "test input"))
            output (view/view state)
            input-row (extract-row output 23)]
        (should-not-be-nil input-row)
        (should-contain "test input" (ansi/strip-ansi input-row))))

    (it "has help bar on last row"
      (let [state (-> (core/init-state) (core/set-dimensions 80 24))
            output (view/view state)
            help-row (extract-row output 24)]
        (should-not-be-nil help-row)
        (should-contain "Ctrl+C" (ansi/strip-ansi help-row))))

    (it "uses cursor positioning for each line"
      (let [state (-> (core/init-state) (core/set-dimensions 80 24))
            output (view/view state)]
        ;; Should contain move-to for row 1 and row 24
        (should-contain "\u001b[1;1H" output)
        (should-contain "\u001b[24;1H" output)))

    (it "includes error when present"
      (let [state (-> (core/init-state)
                      (core/set-dimensions 80 24)
                      (core/set-error "Something broke"))
            output (view/view state)]
        (should-contain "Something broke" (ansi/strip-ansi output))))

    (it "renders all height rows"
      (let [state (-> (core/init-state) (core/set-dimensions 80 24))
            output (view/view state)]
        ;; Every row from 1 to 24 should have a positioning escape
        (doseq [row (range 1 25)]
          (should-contain (str "\u001b[" row ";1H") output))))

    (it "includes conversation when messages exist"
      (let [state (-> (core/init-state)
                      (core/set-dimensions 80 24)
                      (core/add-message {:role :user :content "Hello"}))
            output (view/view state)]
        (should-contain "Hello" (ansi/strip-ansi output))))))
