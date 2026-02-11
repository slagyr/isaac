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
   When scroll-offset > 0, shows older messages shifted from the end.
   Returns a vector of strings."
  [state available-rows]
  (let [messages (:messages state)]
    (if (empty? messages)
      (let [help-line (str "  " (ansi/dim "Type a message to chat with Isaac"))
            padding   (dec available-rows)]
        (into (vec (repeat padding "")) [help-line]))
      (let [all-lines  (mapv format-message messages)
            total      (count all-lines)
            offset     (min (:scroll-offset state 0) (max 0 (- total available-rows)))
            end-idx    (- total offset)
            start-idx  (max 0 (- end-idx available-rows))
            msg-lines  (subvec all-lines start-idx end-idx)
            padding    (- available-rows (count msg-lines))]
        (into (vec (repeat padding "")) msg-lines)))))

;; -- Input Line --

(defn render-input-line [state]
  (str (ansi/bold "> ") (:input state) (ansi/dim "_")))

;; -- Help Bar --

(defn render-help-bar [state]
  (let [exhausted?   (and (= :disconnected (:connection-status state))
                          (>= (:reconnect-attempts state 0) 6))
        scrolled?    (pos? (:scroll-offset state 0))
        parts        (cond-> ["Ctrl+C:quit"]
                       exhausted? (conj "R:reconnect")
                       scrolled?  (conj "more below"))
        help-text    (str/join " | " parts)]
    (ansi/dim help-text)))

;; -- Error --

(defn render-error [state]
  (when-let [error (:error state)]
    (ansi/red (str " !! ERROR: " error))))

;; -- Full Screen Layout --

(defn- render-line-at
  "Renders a line at absolute row position (1-based). Clears rest of line."
  [row content]
  (str (ansi/move-to row 1) content ansi/clear-line))

(defn view
  "Renders the full-screen UI using absolute cursor positioning.
   Each line is placed at its exact row, preventing scroll artifacts."
  [state]
  (let [height    (:height state 24)
        width     (:width state 80)
        separator (ansi/dim (ansi/horizontal-line width))
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
         (map-indexed (fn [idx line] (render-line-at (inc idx) line)))
         (str/join))))
