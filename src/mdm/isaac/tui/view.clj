(ns mdm.isaac.tui.view
  "View rendering for Isaac terminal client.
   Pure functions that transform state into strings."
  (:require [clojure.string :as str]
            [mdm.isaac.tui.core :as core]))

;; Status indicators
(def ^:private status-icons
  {:connected    "[+]"
   :disconnected "[-]"
   :connecting   "[~]"
   :reconnecting "[~]"})

(def ^:private goal-status-icons
  {:active    "[*]"
   :resolved  "[v]"
   :abandoned "[x]"})

(def ^:private type-icons
  {:thought  "."
   :insight  "!"
   :question "?"
   :share    ">"})

(defn- parse-host-port
  "Extracts host:port from a WebSocket URI."
  [uri]
  (when uri
    (try
      (let [java-uri (java.net.URI. uri)]
        (str (.getHost java-uri) ":" (.getPort java-uri)))
      (catch Exception _ nil))))

(defn- connection-text
  "Returns the display text for connection status."
  [state]
  (let [conn-status       (:connection-status state)
        attempts          (:reconnect-attempts state 0)
        exhausted-retries? (and (= :disconnected conn-status)
                                (>= attempts 6))]
    (case conn-status
      :connected    "Connected"
      :reconnecting "Reconnecting..."
      :disconnected (if exhausted-retries?
                      "Disconnected - Press R to retry"
                      "Disconnected")
      :connecting   "Connecting..."
      "Disconnected")))

(defn render-status
  "Renders the status bar showing connection and thinking status."
  [state]
  (let [conn-status (:connection-status state)
        conn-text   (connection-text state)
        conn-icon   (get status-icons conn-status "[-]")
        host-port   (parse-host-port (:server-uri state))
        thinking    (core/current-thinking state)]
    (str "Isaac " conn-icon " " conn-text
         (when host-port (str " @ " host-port))
         (when thinking (str " | " thinking)))))

(defn- panel-header [title active?]
  (if active?
    (str ">> " title " <<")
    (str "== " title " ==")))

(defn render-goals
  "Renders the goals panel."
  [state]
  (let [goals   (:goals state)
        active? (= :goals (:active-panel state))]
    (str (panel-header "Goals" active?) "\n"
         (if (empty? goals)
           "  No goals"
           (->> goals
                (map (fn [g]
                       (let [icon (get goal-status-icons (:status g) "[?]")]
                         (str "  " icon " " (:content g)
                              " [" (name (:status g)) "]"))))
                (str/join "\n"))))))

(def ^:private max-thoughts-displayed 15)

(defn render-thoughts
  "Renders the thoughts panel."
  [state]
  (let [thoughts (:thoughts state)
        active?  (= :thoughts (:active-panel state))
        total    (count thoughts)
        display  (take max-thoughts-displayed thoughts)
        hidden   (- total (count display))]
    (str (panel-header "Thoughts" active?) "\n"
         (if (empty? thoughts)
           "  No thoughts"
           (str (->> display
                     (map (fn [t]
                            (let [icon (get type-icons (:type t) ".")]
                              (str "  " icon " " (:content t)))))
                     (str/join "\n"))
                (when (pos? hidden)
                  (str "\n  ... " hidden " more")))))))

(def ^:private max-shares-displayed 10)

(defn render-shares
  "Renders the shares panel."
  [state]
  (let [shares  (:shares state)
        active? (= :shares (:active-panel state))
        total   (count shares)
        display (take max-shares-displayed shares)
        hidden  (- total (count display))]
    (str (panel-header "Shares" active?) "\n"
         (if (empty? shares)
           "  No shares"
           (str (->> display
                     (map (fn [s]
                            (str "  > " (:content s))))
                     (str/join "\n"))
                (when (pos? hidden)
                  (str "\n  ... " hidden " more")))))))

(def ^:private max-messages-displayed 10)

(defn render-conversation
  "Renders the conversation panel."
  [state]
  (let [messages (:messages state)
        total    (count messages)
        display  (take-last max-messages-displayed messages)
        hidden   (- total (count display))]
    (str "== Conversation ==\n"
         (if (empty? messages)
           "  Type a message to chat with Isaac"
           (str (when (pos? hidden)
                  (str "  ... " hidden " earlier messages\n"))
                (->> display
                     (map (fn [m]
                            (let [role-label (if (= :user (:role m)) "You" "Isaac")]
                              (str "  " role-label ": " (:content m)))))
                     (str/join "\n")))))))

(defn render-input
  "Renders the input line."
  [state]
  (str "> " (:input state) "_"))

(defn render-help
  "Renders help text showing key bindings."
  [state]
  (let [base-help "Tab:panel | /goals | /add <goal> | /thoughts | /search <query> | /shares"
        exhausted? (and (= :disconnected (:connection-status state))
                        (>= (:reconnect-attempts state 0) 6))]
    (if exhausted?
      (str base-help " | R:reconnect")
      base-help)))

(defn render-error
  "Renders error message if present."
  [state]
  (when-let [error (:error state)]
    (str "!! ERROR: " error)))

(defn view
  "Main view function - renders entire UI."
  [state]
  (str/join "\n\n"
            (filterv some?
                     [(render-status state)
                      (render-error state)
                      (render-conversation state)
                      (render-goals state)
                      (render-thoughts state)
                      (render-shares state)
                      (render-input state)
                      (render-help state)])))
