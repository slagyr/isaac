(ns mdm.isaac.tui.update
  "Update function for Isaac terminal client.
   Handles keyboard input, commands, and WebSocket messages.
   Returns [new-state cmd] tuple following Elm Architecture."
  (:require [mdm.isaac.tui.command :as command]
            [mdm.isaac.tui.core :as core]))

;; Command parsing - delegates to command module (OCP compliant)

(defn parse-command
  "Parses user input into a command structure.
   Delegates to command/parse-input for OCP-compliant command dispatch."
  [text]
  (command/parse-input text))

;; Keyboard handlers - delegates to command module (OCP compliant)

(defn- handle-key-press
  "Handle keyboard input.
   Delegates to command/handle-key-input for OCP-compliant key dispatch."
  [state msg]
  (command/handle-key-input state (:key msg)))

;; WebSocket message handlers

(defn- handle-ws-message
  "Handle incoming WebSocket message. Clears any previous error on success."
  [state msg]
  (let [state' (core/clear-error state)]
    (case (:action msg)
      :chat/send-user  [(core/add-message state' {:role :user :content (-> msg :payload :content)}) nil]
      :chat/send       [(core/add-message state' {:role :isaac :content (-> msg :payload :response)}) nil]
      ;; Default - ignore unknown actions
      [state' nil])))

(defn- handle-ws-error
  "Handle WebSocket error by setting error message on state."
  [state msg]
  [(core/set-error state (:message msg)) nil])

(defn- handle-ws-connect
  "Handle WebSocket connection established."
  [state]
  [(core/set-connection-status state :connected) nil])

(defn- handle-ws-disconnect
  "Handle WebSocket disconnection. Returns command to trigger reconnect if appropriate."
  [state msg]
  (let [auto-reconnect?  (:auto-reconnect msg)
        current-attempts (:reconnect-attempts state 0)
        delay-ms         (core/reconnect-delay current-attempts)
        new-state        (core/increment-reconnect-attempts state)
        should-retry?    (core/should-retry? new-state)]
    (if (and auto-reconnect? should-retry?)
      ;; Set to reconnecting and return reconnect command with delay
      [(core/set-connection-status new-state :reconnecting)
       {:type :reconnect :delay-ms delay-ms}]
      ;; No more retries - set to disconnected
      [(core/set-connection-status new-state :disconnected) nil])))

;; Main update function

(defn- handle-paste
  "Handle pasted text."
  [state msg]
  [(core/append-input state (:text msg)) nil])

(defn- handle-scroll-up
  "Handle scroll up (toward older messages)."
  [state msg]
  [(core/scroll-up state (:visible-rows msg)) nil])

(defn- handle-scroll-down
  "Handle scroll down (toward newer messages)."
  [state]
  [(core/scroll-down state) nil])

(defn update-fn
  "Main update function for Elm Architecture.
   Takes current state and a message, returns [new-state cmd]."
  [state msg]
  (case (:type msg)
    :key-press     (handle-key-press state msg)
    :paste         (handle-paste state msg)
    :scroll-up     (handle-scroll-up state msg)
    :scroll-down   (handle-scroll-down state)
    :ws-message    (handle-ws-message state msg)
    :ws-error      (handle-ws-error state msg)
    :ws-connect    (handle-ws-connect state)
    :ws-disconnect (handle-ws-disconnect state msg)
    ;; Default - ignore unknown message types
    [state nil]))
