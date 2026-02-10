(ns mdm.isaac.tui.core
  "Core state management for Isaac terminal client.
   Follows Elm Architecture pattern with pure state transformations.")

;; Panel order for cycling
(def ^:private panels [:goals :thoughts :shares])

;; Reconnection configuration
(def ^:private max-reconnect-attempts 6)
(def ^:private max-delay-ms 30000)
(def ^:private base-delay-ms 1000)

(defn init-state
  "Creates the initial application state."
  ([] (init-state nil))
  ([server-uri]
   {:goals              []
    :thoughts           []
    :shares             []
    :messages           []
    :conversation-id    nil
    :connection-status  :disconnected
    :server-uri         server-uri
    :input              ""
    :active-panel       :goals
    :reconnect-attempts 0}))

;; State transformation functions (pure)

(defn set-goals [state goals]
  (assoc state :goals goals))

(defn add-goal [state goal]
  (update state :goals conj goal))

(defn set-thoughts [state thoughts]
  (assoc state :thoughts thoughts))

(defn set-shares [state shares]
  (assoc state :shares shares))

(defn set-connection-status [state status]
  (cond-> (assoc state :connection-status status)
    (= :connected status) (assoc :reconnect-attempts 0)))

(defn set-error [state message]
  (assoc state :error message))

(defn clear-error [state]
  (dissoc state :error))

(defn set-input [state text]
  (assoc state :input text))

(defn append-input [state text]
  (update state :input str text))

(defn backspace-input [state]
  (update state :input #(if (empty? %) "" (subs % 0 (dec (count %))))))

(defn clear-input [state]
  (assoc state :input ""))

(defn set-active-panel [state panel]
  (assoc state :active-panel panel))

(defn cycle-panel [state]
  (let [current (:active-panel state)
        idx     (.indexOf panels current)
        next-idx (mod (inc idx) (count panels))]
    (assoc state :active-panel (nth panels next-idx))))

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

;; Derived state functions

(defn current-thinking
  "Returns description of what Isaac is currently thinking about."
  [state]
  (when-let [active-goal (->> (:goals state)
                              (filter #(= :active (:status %)))
                              first)]
    (str "Thinking about \"" (:content active-goal) "\"")))

;; Conversation state management

(defn set-conversation-id [state conversation-id]
  (assoc state :conversation-id conversation-id))

(defn add-message [state message]
  (update state :messages conj message))

(defn set-messages [state messages]
  (assoc state :messages messages))

(defn clear-messages [state]
  (assoc state :messages []))
