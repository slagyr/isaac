(ns mdm.isaac.tui.core
  "Core state management for Isaac terminal client.
   Follows Elm Architecture pattern with pure state transformations.")

;; Reconnection configuration
(def ^:private max-reconnect-attempts 6)
(def ^:private max-delay-ms 30000)
(def ^:private base-delay-ms 1000)

(defn init-state
  "Creates the initial application state."
  ([] (init-state nil))
  ([server-uri]
   {:messages           []
    :conversation-id    nil
    :connection-status  :disconnected
    :server-uri         server-uri
    :input              ""
    :width              80
    :height             24
    :reconnect-attempts 0
    :scroll-offset      0}))

;; Terminal dimensions

(defn set-dimensions [state width height]
  (assoc state :width width :height height))

;; Input state transformations (pure)

(defn set-input [state text]
  (assoc state :input text))

(defn append-input [state text]
  (update state :input str text))

(defn backspace-input [state]
  (update state :input #(if (empty? %) "" (subs % 0 (dec (count %))))))

(defn clear-input [state]
  (assoc state :input ""))

;; Connection state

(defn set-connection-status [state status]
  (cond-> (assoc state :connection-status status)
    (= :connected status) (assoc :reconnect-attempts 0)))

(defn set-error [state message]
  (assoc state :error message))

(defn clear-error [state]
  (dissoc state :error))

;; Reconnect state management

(defn increment-reconnect-attempts [state]
  (update state :reconnect-attempts inc))

(defn reset-reconnect-attempts [state]
  (assoc state :reconnect-attempts 0))

(defn reconnect-delay
  "Calculate delay in ms for reconnect attempt using exponential backoff.
   Sequence: 1s, 2s, 4s, 8s, 16s, then capped at 30s."
  [attempt]
  (min max-delay-ms (* base-delay-ms (long (Math/pow 2 attempt)))))

(defn should-retry?
  "Returns true if we should attempt another reconnect."
  [state]
  (< (:reconnect-attempts state) max-reconnect-attempts))

;; Conversation state management

(defn set-conversation-id [state conversation-id]
  (assoc state :conversation-id conversation-id))

(defn add-message [state message]
  (-> state
      (update :messages conj message)
      (assoc :scroll-offset 0)))

(defn set-messages [state messages]
  (assoc state :messages messages))

(defn clear-messages [state]
  (assoc state :messages []))

;; Scroll state management

(defn scroll-up
  "Scroll up (toward older messages) by 3 lines, clamped to max scrollable."
  [state visible-rows]
  (let [total    (count (:messages state))
        max-off  (max 0 (- total visible-rows))
        new-off  (min max-off (+ (:scroll-offset state) 3))]
    (assoc state :scroll-offset new-off)))

(defn scroll-down
  "Scroll down (toward newer messages) by 3 lines, clamped to 0."
  [state]
  (assoc state :scroll-offset (max 0 (- (:scroll-offset state) 3))))

(defn reset-scroll
  "Reset scroll to bottom (offset 0)."
  [state]
  (assoc state :scroll-offset 0))
