(ns mdm.isaac.tui.core
  "Core state management for Isaac terminal client.
   Follows Elm Architecture pattern with pure state transformations.")

;; Panel order for cycling
(def ^:private panels [:goals :thoughts :shares])

(defn init-state
  "Creates the initial application state."
  ([] (init-state nil))
  ([server-uri]
   {:goals             []
    :thoughts          []
    :shares            []
    :connection-status :disconnected
    :server-uri        server-uri
    :input             ""
    :active-panel      :goals}))

;; State transformation functions (pure)

(defn set-goals [state goals]
  (assoc state :goals goals))

(defn set-thoughts [state thoughts]
  (assoc state :thoughts thoughts))

(defn set-shares [state shares]
  (assoc state :shares shares))

(defn set-connection-status [state status]
  (assoc state :connection-status status))

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

;; Derived state functions

(defn current-thinking
  "Returns description of what Isaac is currently thinking about."
  [state]
  (when-let [active-goal (->> (:goals state)
                              (filter #(= :active (:status %)))
                              first)]
    (str "Thinking about \"" (:content active-goal) "\"")))
