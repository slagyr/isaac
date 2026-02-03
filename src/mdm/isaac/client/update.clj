(ns mdm.isaac.client.update
  "Update function for Isaac terminal client.
   Handles keyboard input, commands, and WebSocket messages.
   Returns [new-state cmd] tuple following Elm Architecture."
  (:require [clojure.string :as str]
            [mdm.isaac.client.core :as core]))

;; Command parsing


;; TODO (isaac-dsu) - MDM: This cond violates OCP.  It seems to me that we should create a "command" deftype that has a name or id
;;  and an action.  example: (->TuiCommand "goals" (fn [] do the thing)).  We can omit the intermediate keyword
;;  (eg :goals/list).  These commands would be stored in a list, or better a map for quick retrieval.
(defn parse-command
  "Parses user input into a command structure."
  [text]
  (let [trimmed (str/trim text)]
    (cond
      ;; /goals - list goals
      (= "/goals" trimmed)
      {:action :goals/list}

      ;; /add <content> - add a goal
      (str/starts-with? trimmed "/add ")
      {:action  :goals/add
       :content (str/trim (subs trimmed 5))}

      ;; /thoughts - recent thoughts
      (= "/thoughts" trimmed)
      {:action :thoughts/recent}

      ;; /shares - unread shares
      (= "/shares" trimmed)
      {:action :shares/unread}

      ;; /search <query> - search thoughts
      (str/starts-with? trimmed "/search ")
      {:action :thoughts/search
       :query  (str/trim (subs trimmed 8))}

      ;; Anything else is chat
      :else
      {:action :chat
       :text   trimmed})))

;; Keyboard handlers

(defn- handle-enter
  "Handle enter key - send command/message."
  [state]
  (let [input (:input state)]
    (if (empty? (str/trim input))
      [state nil]
      [(core/clear-input state)
       {:type :send :text input}])))

(defn- regular-key?
  "Returns true if key is a regular character (char or single-char string)."
  [key]
  (or (char? key)
      (and (string? key)
           (= 1 (count key))
           (not (str/starts-with? key "ctrl+")))))

(defn- key=
  "Compares key to expected value, handling both char and string."
  [key expected]
  (or (= key expected)
      (and (char? key) (= (str key) expected))
      (and (string? key) (= 1 (count key)) (= (first key) expected))))


;; TODO (isaac-dsu) - MDM: Similar to commands above, we should store these KeyCommands as deftype(s) in a list.
(defn- handle-key-press
  "Handle keyboard input."
  [state msg]
  (let [key (:key msg)]
    (cond
      ;; Quit
      (or (key= key "q") (key= key "ctrl+c") (= key :q))
      [state :quit]

      ;; Tab - cycle panels
      (= :tab key)
      [(core/cycle-panel state) nil]

      ;; Backspace
      (= :backspace key)
      [(core/backspace-input state) nil]

      ;; Enter - send
      (= :enter key)
      (handle-enter state)

      ;; Regular character
      (regular-key? key)
      [(core/append-input state (str key)) nil]

      ;; Unknown key - ignore
      :else
      [state nil])))

;; WebSocket message handlers

(defn- handle-ws-message
  "Handle incoming WebSocket message."
  [state msg]
  (case (:action msg)
    :goals/list      [(core/set-goals state (:payload msg)) nil]
    :thoughts/recent [(core/set-thoughts state (:payload msg)) nil]
    :shares/unread   [(core/set-shares state (:payload msg)) nil]
    ;; Default - ignore unknown actions
    [state nil]))

(defn- handle-ws-connect
  "Handle WebSocket connection established."
  [state]
  [(core/set-connection-status state :connected) nil])

(defn- handle-ws-disconnect
  "Handle WebSocket disconnection."
  [state]
  [(core/set-connection-status state :disconnected) nil])

;; Main update function

(defn update-fn
  "Main update function for Elm Architecture.
   Takes current state and a message, returns [new-state cmd]."
  [state msg]
  (case (:type msg)
    :key-press     (handle-key-press state msg)
    :ws-message    (handle-ws-message state msg)
    :ws-connect    (handle-ws-connect state)
    :ws-disconnect (handle-ws-disconnect state)
    ;; Default - ignore unknown message types
    [state nil]))
