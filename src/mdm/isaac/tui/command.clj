(ns mdm.isaac.tui.command
  "Command protocol and implementations for TUI.
   Follows OCP - new commands can be added without modifying existing code."
  (:require [clojure.string :as str]
            [mdm.isaac.tui.core :as core]))

(defprotocol Command
  "Protocol for TUI commands."
  (matches? [this input] "Returns true if this command matches the input.")
  (parse [this input] "Parses input and returns command map with :action and data."))

;; Command implementations

(def chat-command
  (reify Command
    (matches? [_ input] (not (str/blank? input)))
    (parse [_ input] {:action :chat :text (str/trim input)})))

;; Command registry - chat is the only command now
(def commands
  [chat-command])

(defn parse-input
  "Parses input using the command registry.
   Returns the result of the first matching command."
  [input]
  (let [trimmed (str/trim input)]
    (when-let [cmd (first (filter #(matches? % trimmed) commands))]
      (parse cmd trimmed))))

;; KeyHandler protocol for keyboard input (OCP compliant)

(defprotocol KeyHandler
  "Protocol for handling keyboard input."
  (key-matches? [this key] "Returns true if this handler matches the key.")
  (handle-key [this state key] "Handles the key, returns [new-state cmd]."))

;; Helper functions

(defn- key=
  "Compares key to expected value, handling both char and string."
  [key expected]
  (or (= key expected)
      (and (char? key) (= (str key) expected))
      (and (string? key) (= 1 (count key)) (= (first key) expected))))

(defn- regular-key?
  "Returns true if key is a regular character (char or single-char string)."
  [key]
  (or (char? key)
      (and (string? key)
           (= 1 (count key))
           (not (str/starts-with? key "ctrl+")))))

;; KeyHandler implementations

(def quit-handler
  (reify KeyHandler
    (key-matches? [_ key] (key= key "ctrl+c"))
    (handle-key [_ state _] [state :quit])))

(def backspace-handler
  (reify KeyHandler
    (key-matches? [_ key] (= :backspace key))
    (handle-key [_ state _] [(core/backspace-input state) nil])))

(def enter-handler
  (reify KeyHandler
    (key-matches? [_ key] (= :enter key))
    (handle-key [_ state _]
      (let [input (:input state)]
        (if (empty? (str/trim input))
          [state nil]
          (let [parsed (parse-input input)
                new-state (if (= :chat (:action parsed))
                            (-> state
                                (core/clear-input)
                                (core/add-message {:role :user :content (str/trim input)}))
                            (core/clear-input state))]
            [new-state {:type :send :text input}]))))))

(defn- should-trigger-reconnect?
  "Returns true if R key should trigger reconnect (disconnected with exhausted retries)."
  [state]
  (and (= :disconnected (:connection-status state))
       (>= (:reconnect-attempts state 0) 6)))

(def reconnect-handler
  (reify KeyHandler
    (key-matches? [_ key] (key= key "R"))
    (handle-key [_ state _]
      (if (should-trigger-reconnect? state)
        [(core/reset-reconnect-attempts state) {:type :reconnect}]
        ;; Not in reconnect state - treat as regular input
        [(core/append-input state "R") nil]))))

(def regular-key-handler
  (reify KeyHandler
    (key-matches? [_ key] (regular-key? key))
    (handle-key [_ state key] [(core/append-input state (str key)) nil])))

;; Fallback handler for unknown keys
(def ignore-handler
  (reify KeyHandler
    (key-matches? [_ _] true)
    (handle-key [_ state _] [state nil])))

;; KeyHandler registry - ordered by priority
(def key-handlers
  [quit-handler
   backspace-handler
   enter-handler
   reconnect-handler
   regular-key-handler
   ignore-handler])

(defn handle-key-input
  "Handles keyboard input using the key handler registry.
   Returns [new-state cmd] tuple."
  [state key]
  (when-let [handler (first (filter #(key-matches? % key) key-handlers))]
    (handle-key handler state key)))
