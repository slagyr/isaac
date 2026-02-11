(ns mdm.isaac.tui.view
  "View rendering for Isaac terminal client.
   Pure functions that transform state into a full-screen terminal layout.

   Layout (top to bottom):
     Row 0:      Status bar (inverse, full width)
     Row 1:      Separator line (dim dashes)
     Row 2..N-4: Chat area (bottom-aligned, fills available space)
     Row N-3:    Separator line (dim dashes)
     Row N-2:    Input line
     Row N-1:    Help bar (dim)
     (N = terminal height)"
  (:require [clojure.string :as str]
            [mdm.isaac.tui.ansi :as ansi]))

;; -- Status Bar --

(defn- parse-host-port
  "Extracts host:port from a WebSocket URI."
  [uri]
  (when uri
    (try
      (let [java-uri (java.net.URI. uri)]
        (str (.getHost java-uri) ":" (.getPort java-uri)))
      (catch Exception _ nil))))

(defn- connection-text [state]
  (let [conn-status (:connection-status state)
        attempts    (:reconnect-attempts state 0)
        exhausted?  (and (= :disconnected conn-status) (>= attempts 6))]
    (case conn-status
      :connected    (ansi/green "Connected")
      :reconnecting (ansi/yellow "Reconnecting...")
      :disconnected (if exhausted?
                      (ansi/red "Disconnected - Press R to retry")
                      (ansi/red "Disconnected"))
      :connecting   (ansi/yellow "Connecting...")
      (ansi/red "Disconnected"))))

(defn render-status-bar
  "Renders the status bar. Full terminal width, inverse colors."
  [state]
  (let [width     (:width state 80)
        host-port (parse-host-port (:server-uri state))
        content   (str " Isaac " (connection-text state)
                       (when host-port (str " @ " host-port))
                       " ")]
    (ansi/inverse (ansi/pad-right content width))))

;; -- Chat Area --

(defn- format-message [{:keys [role content]}]
  (if (= :user role)
    (str "  " (ansi/cyan "You: ") content)
    (str "  " (ansi/green "Isaac: ") content)))

(defn render-messages
  "Renders messages into exactly `available-rows` lines, bottom-aligned.
   Returns a vector of strings."
  [state available-rows]
  (let [messages (:messages state)]
    (if (empty? messages)
      (let [help-line (str "  " (ansi/dim "Type a message to chat with Isaac"))
            padding   (dec available-rows)]
        (into (vec (repeat padding "")) [help-line]))
      (let [msg-lines (->> messages
                           (map format-message)
                           (take-last available-rows)
                           vec)
            padding   (- available-rows (count msg-lines))]
        (into (vec (repeat padding "")) msg-lines)))))

;; -- Input Line --

(defn render-input-line [state]
  (str (ansi/bold "> ") (:input state) (ansi/dim "_")))

;; -- Help Bar --

(defn render-help-bar [state]
  (let [exhausted? (and (= :disconnected (:connection-status state))
                        (>= (:reconnect-attempts state 0) 6))
        help-text  (if exhausted?
                     "Ctrl+C:quit | R:reconnect"
                     "Ctrl+C:quit")]
    (ansi/dim help-text)))

;; -- Error --

(defn render-error [state]
  (when-let [error (:error state)]
    (ansi/red (str " !! ERROR: " error))))

;; -- Full Screen Layout --

(defn- pad-line
  "Pads a line to full width, appending a clear-to-end-of-line escape.
   This prevents ghosting from previous renders."
  [line]
  (str line "\u001b[K"))

(defn view
  "Renders the full-screen UI. Returns a string with exactly `height` lines."
  [state]
  (let [height    (:height state 24)
        width     (:width state 80)
        separator (ansi/dim (ansi/horizontal-line width))
        ;; Fixed rows: status(1) + sep(1) + sep(1) + input(1) + help(1) = 5
        ;; If error present, it takes 1 row from chat area
        error     (render-error state)
        fixed     (if error 6 5)
        chat-rows (max 1 (- height fixed))
        messages  (render-messages state chat-rows)
        lines     (cond-> [(render-status-bar state)
                           separator]
                    error (conj error)
                    true  (into messages)
                    true  (conj separator)
                    true  (conj (render-input-line state))
                    true  (conj (render-help-bar state)))]
    (->> lines
         (map pad-line)
         (str/join "\n"))))
