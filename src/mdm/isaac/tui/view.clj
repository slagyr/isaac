(ns mdm.isaac.tui.view
  "View rendering for Isaac terminal client.
   Pure functions that transform state into strings."
  (:require [clojure.string :as str]))

;; Status indicators
(def ^:private status-icons
  {:connected    "[+]"
   :disconnected "[-]"
   :connecting   "[~]"
   :reconnecting "[~]"})

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
  "Renders the status bar showing connection status."
  [state]
  (let [conn-status (:connection-status state)
        conn-text   (connection-text state)
        conn-icon   (get status-icons conn-status "[-]")
        host-port   (parse-host-port (:server-uri state))]
    (str "Isaac " conn-icon " " conn-text
         (when host-port (str " @ " host-port)))))

(def ^:private max-messages-displayed 10)

(defn render-conversation
  "Renders the conversation panel."
  [state]
  (let [messages (:messages state)
        total    (count messages)
        display  (take-last max-messages-displayed messages)
        hidden   (- total (count display))]
    (if (empty? messages)
      "  Type a message to chat with Isaac"
      (str (when (pos? hidden)
             (str "  ... " hidden " earlier messages\n"))
           (->> display
                (map (fn [m]
                       (let [role-label (if (= :user (:role m)) "You" "Isaac")]
                         (str "  " role-label ": " (:content m)))))
                (str/join "\n"))))))

(defn render-input
  "Renders the input line."
  [state]
  (str "> " (:input state) "_"))

(defn render-help
  "Renders help text showing key bindings."
  [state]
  (let [exhausted? (and (= :disconnected (:connection-status state))
                        (>= (:reconnect-attempts state 0) 6))]
    (if exhausted?
      "Ctrl+C:quit | R:reconnect"
      "Ctrl+C:quit")))

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
                      (render-input state)
                      (render-help state)])))
