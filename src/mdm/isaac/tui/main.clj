(ns mdm.isaac.tui.main
  "Main entry point for Isaac terminal client.
   Uses JLine directly for terminal I/O with Elm Architecture pattern."
  (:require [clojure.core.async :as async :refer [chan put! close!]]
            [clojure.string :as str]
            [mdm.isaac.config :as config]
            [mdm.isaac.tui.auth :as auth]
            [mdm.isaac.tui.core :as core]
            [mdm.isaac.tui.view :as view]
            [mdm.isaac.tui.update :as update]
            [mdm.isaac.tui.ws :as ws])
  (:import [org.jline.terminal TerminalBuilder Terminal]
           [org.jline.utils NonBlockingReader]))

;; Configuration - use host/port from config
(def default-server-uri (config/ws-url))

;; Global state
(defonce ws-client (atom nil))
(defonce msg-chan (atom nil))
(defonce pending-requests (atom (sorted-map)))  ;; sorted by request-id to ensure FIFO response matching
(defonce running (atom false))
(defonce auth-token (atom nil))
(defonce reconnect-scheduled (atom false))

;; ANSI escape codes
(def ^:private CLEAR-SCREEN "\u001b[2J")
(def ^:private CURSOR-HOME "\u001b[H")
(def ^:private CLEAR-TO-END "\u001b[J")
(def ^:private HIDE-CURSOR "\u001b[?25l")
(def ^:private SHOW-CURSOR "\u001b[?25h")
(def ^:private ENTER-ALT-SCREEN "\u001b[?1049h")
(def ^:private EXIT-ALT-SCREEN "\u001b[?1049l")

(defn render-output
  "Builds the complete output string for flicker-free terminal rendering.
   Returns cursor-home + content + clear-to-end (no full screen clear)."
  [content]
  (str CURSOR-HOME content CLEAR-TO-END))

;; Debug logging
(def ^:private debug-log-file "/tmp/isaac-tui.log")

(defn- debug [& args]
  (spit debug-log-file (str (apply pr-str args) "\n") :append true))

;; WebSocket handlers

(defn- handle-ws-open []
  (debug "WebSocket opened!")
  (when-let [ch @msg-chan]
    (put! ch {:type :ws-connect})))

(defn- server-initiated?
  "Returns true if the message is server-initiated (has :kind, not a response)."
  [raw]
  (and (:kind raw) (not (:response-id raw))))

(defn- handle-ws-message [message]
  (debug "ws-message received:" (subs message 0 (min 100 (count message))))
  (let [raw (try (clojure.edn/read-string message) (catch Exception _ nil))]
    (cond
      ;; Ignore server-initiated messages (ws/hello, ws/ping, etc.)
      (server-initiated? raw)
      (debug "Ignoring server message:" (:kind raw))

      ;; No pending requests - ignore unsolicited messages
      (empty? @pending-requests)
      (debug "Ignoring message with no pending requests")

      ;; Match to first pending request
      :else
      (when-let [ch @msg-chan]
        (let [[req-id action] (first @pending-requests)]
          (debug "matched action:" action "for req-id:" req-id)
          (swap! pending-requests dissoc req-id)
          (let [parsed (ws/parse-response action message)]
            (debug "parsed:" parsed)
            (put! ch parsed)))))))

(declare schedule-reconnect!)

(defn- handle-ws-close [code reason]
  (debug "WebSocket closed! code:" code "reason:" reason)
  (when-let [ch @msg-chan]
    (put! ch {:type :ws-disconnect :auto-reconnect true})))

(defn- handle-ws-error [ex]
  (debug "WebSocket error!" (.getMessage ex))
  (when-let [ch @msg-chan]
    (put! ch {:type :ws-error :message (.getMessage ex)})))

(defn- connect-websocket! [uri]
  (let [token         @auth-token
        client-id     (auth/client-id token)
        connection-id (str (random-uuid))
        ws-uri        (str uri "?ws-csrf-token=" client-id "&connection-id=" connection-id)
        headers       (when token {"Cookie" (str "isaac-token=" token)})
        client        (ws/create-client! ws-uri
                                         {:on-open    handle-ws-open
                                          :on-message handle-ws-message
                                          :on-close   handle-ws-close
                                          :on-error   handle-ws-error
                                          :headers    headers})]
    (reset! ws-client client)
    (ws/connect! client)))

(defn- send-command! [cmd]
  (debug "send-command!:" cmd)
  (when-let [client @ws-client]
    (when (ws/connected? client)
      (let [text    (str/trim (:text cmd))
            req-id  (ws/next-request-id!)
            request {:action :chat/send :request-id req-id :text text}]
        ;; Note: User message already added by enter-handler (optimistic update)
        (swap! pending-requests assoc req-id :chat/send)
        (debug "sending chat request:" req-id)
        (ws/send-message! client (ws/format-request request))))))

;; Terminal I/O

(defn- read-until-paste-end
  "Reads characters until bracket paste end sequence (ESC[201~) is found.
   Returns the accumulated text without the end sequence."
  [^NonBlockingReader reader]
  (let [sb (StringBuilder.)]
    (loop []
      (let [c (.read reader 50)]
        (cond
          ;; Timeout or EOF - return what we have
          (or (= c -2) (= c -1))
          (.toString sb)

          ;; ESC - check for end sequence
          (= c 27)
          (let [c2 (.read reader 50)]
            (if (= c2 91) ;; [
              (let [c3 (.read reader 50)
                    c4 (.read reader 50)
                    c5 (.read reader 50)
                    c6 (.read reader 50)]
                (if (and (= c3 50) (= c4 48) (= c5 49) (= c6 126)) ;; 201~
                  (.toString sb) ;; End of paste
                  (do
                    ;; Not end sequence - append what we read and continue
                    (.append sb (char 27))
                    (.append sb (char c2))
                    (when (>= c3 0) (.append sb (char c3)))
                    (when (>= c4 0) (.append sb (char c4)))
                    (when (>= c5 0) (.append sb (char c5)))
                    (when (>= c6 0) (.append sb (char c6)))
                    (recur))))
              (do
                ;; ESC not followed by [ - append and continue
                (.append sb (char 27))
                (when (>= c2 0) (.append sb (char c2)))
                (recur))))

          ;; Regular character
          :else
          (do
            (.append sb (char c))
            (recur)))))))

(defn- read-key
  "Reads a key from terminal. Returns a map with :type and :key.
   Handles bracket paste mode sequences."
  [^NonBlockingReader reader]
  (let [c (.read reader 100)]
    (cond
      (= c -2) nil  ;; timeout
      (= c -1) {:type :key-press :key :eof}
      (= c 27) ;; ESC - check for escape sequence
      (let [c2 (.read reader 50)]
        (if (or (= c2 -1) (= c2 -2))
          {:type :key-press :key :escape}
          (let [c3 (.read reader 50)]
            (cond
              ;; Arrow keys
              (and (= c2 91) (= c3 65)) {:type :key-press :key :up}
              (and (= c2 91) (= c3 66)) {:type :key-press :key :down}
              (and (= c2 91) (= c3 67)) {:type :key-press :key :right}
              (and (= c2 91) (= c3 68)) {:type :key-press :key :left}

              ;; Bracket paste start: ESC[200~
              (and (= c2 91) (= c3 50)) ;; ESC [ 2
              (let [c4 (.read reader 50)
                    c5 (.read reader 50)
                    c6 (.read reader 50)]
                (if (and (= c4 48) (= c5 48) (= c6 126)) ;; 00~
                  {:type :paste :text (read-until-paste-end reader)}
                  {:type :key-press :key :escape}))

              :else {:type :key-press :key :escape}))))
      (= c 9)  {:type :key-press :key :tab}
      (= c 10) {:type :key-press :key :enter}
      (= c 13) {:type :key-press :key :enter}
      (= c 127) {:type :key-press :key :backspace}
      (= c 3)  {:type :key-press :key "ctrl+c"}  ;; Ctrl+C
      (< c 32) {:type :key-press :key (str "ctrl+" (char (+ c 96)))}
      :else    {:type :key-press :key (char c)})))

(defn- terminal-dimensions
  "Returns [width height] of the terminal."
  [^Terminal terminal]
  (let [size (.getSize terminal)]
    [(.getColumns size) (.getRows size)]))

(defn- update-dimensions
  "Updates state with current terminal dimensions."
  [^Terminal terminal state]
  (let [[w h] (terminal-dimensions terminal)]
    (core/set-dimensions state w h)))

(defn- render!
  "Renders the view to the terminal."
  [^Terminal terminal state]
  (let [writer (.writer terminal)]
    (.print writer (render-output (view/view state)))
    (.flush writer)))

(defn- schedule-reconnect!
  "Schedules a reconnection attempt after the specified delay."
  [server-uri delay-ms]
  (when (compare-and-set! reconnect-scheduled false true)
    (debug "Scheduling reconnect in" delay-ms "ms")
    (future
      (Thread/sleep delay-ms)
      (reset! reconnect-scheduled false)
      (when @running
        (debug "Attempting reconnect...")
        (try
          ;; Close existing client if any
          (when-let [client @ws-client]
            (try (ws/close! client) (catch Exception _)))
          (reset! pending-requests (sorted-map))
          (connect-websocket! server-uri)
          (catch Exception e
            (debug "Reconnect failed:" (.getMessage e))
            (when-let [ch @msg-chan]
              (put! ch {:type :ws-disconnect :auto-reconnect true}))))))))

(defn- process-ws-messages
  "Process any pending WebSocket messages. Returns [new-state cmd]."
  [state]
  (loop [s state
         cmd nil]
    (if-let [ch @msg-chan]
      (if-let [msg (async/poll! ch)]
        (let [[new-s new-cmd] (update/update-fn s msg)]
          (recur new-s (or new-cmd cmd)))
        [s cmd])
      [s cmd])))

(defn- handle-command!
  "Handles a command, performing side effects. Returns true if should continue loop."
  [cmd server-uri]
  (cond
    (= :quit cmd)
    (do (reset! running false) false)

    (= :reconnect (:type cmd))
    (do
      (schedule-reconnect! server-uri (or (:delay-ms cmd) 1000))
      true)

    (= :send (:type cmd))
    (do
      (send-command! cmd)
      true)

    :else true))

(defn- main-loop
  "Main event loop."
  [^Terminal terminal ^NonBlockingReader reader server-uri]
  ;; Connect to WebSocket in background
  (future
    (Thread/sleep 500)
    (try
      (connect-websocket! server-uri)
      (catch Exception e
        (when-let [ch @msg-chan]
          (put! ch {:type :ws-error
                    :message (str "Connection failed: " (.getMessage e))})))))

  ;; Main loop - track previous state to avoid unnecessary re-renders
  (loop [state (update-dimensions terminal (core/init-state server-uri))
         prev-state nil]
    (when @running
      ;; Process WebSocket messages
      (let [[state' ws-cmd] (process-ws-messages state)
            ;; Update terminal dimensions each cycle (handles resize)
            state' (update-dimensions terminal state')]
        ;; Handle any command from WS processing
        (when ws-cmd
          (handle-command! ws-cmd server-uri))
        ;; Only render when state has changed
        (when (not= state' prev-state)
          (render! terminal state'))
        ;; Read input
        (if-let [key-event (read-key reader)]
          (let [[new-state cmd] (update/update-fn state' key-event)]
            (if (handle-command! cmd server-uri)
              (recur new-state state')
              nil))  ;; quit
          ;; No input, just continue loop
          (recur state' state'))))))

(defn- try-saved-token
  "Tries to use saved token. Returns true if valid token was loaded."
  []
  (when-let [token (auth/load-token)]
    (when (auth/token-valid? token)
      (reset! auth-token token)
      true)))

(defn- read-password
  "Reads password with hidden input using System/console, falls back to read-line."
  []
  (if-let [console (System/console)]
    (String. (.readPassword console))
    (read-line)))

(defn- prompt-login
  "Prompts user for credentials and attempts login. Returns true on success."
  [server-uri]
  (let [base-url (auth/ws-uri->http-base server-uri)]
    (println "Isaac Terminal Client")
    (println "Server:" server-uri)
    (println)
    (print "Email: ")
    (flush)
    (let [email (read-line)]
      (print "Password: ")
      (flush)
      (let [password (read-password)
            result (auth/login base-url email password)]
        (if (:ok result)
          (do
            (reset! auth-token (:token result))
            (auth/save-token! (:token result))
            (println "Login successful!")
            (Thread/sleep 500)
            true)
          (do
            (println "Login failed:" (:error result))
            false))))))

(defn- authenticate
  "Attempts auto-login with saved token, falls back to prompt."
  [server-uri]
  (or (try-saved-token)
      (prompt-login server-uri)))

(defn- rlwrap-active?
  "Returns true if the process is running under rlwrap."
  []
  (try
    (some-> (java.lang.ProcessHandle/current)
            (.parent)
            (.orElse nil)
            (.info)
            (.command)
            (.orElse "")
            (str/includes? "rlwrap"))
    (catch Exception _ false)))

(defn run
  "Runs the Isaac terminal client."
  ([] (run default-server-uri))
  ([server-uri]
   (when (rlwrap-active?)
     (println "WARNING: rlwrap detected. Use 'clojure -M:tui' or 'bin/isaac' instead of 'clj -M:tui'.")
     (println "rlwrap conflicts with the TUI's terminal handling.")
     (println)
     (Thread/sleep 2000))
   ;; Authenticate first (before entering raw mode)
(if (authenticate server-uri)
      (do
        (reset! msg-chan (chan 100))
        (reset! pending-requests (sorted-map))
        (reset! running true)

       (let [terminal (-> (TerminalBuilder/builder)
                          (.system true)
                          (.build))
             reader (.reader terminal)]
          (try
            ;; Enter raw mode + alternate screen buffer
            (.enterRawMode terminal)
            (let [writer (.writer terminal)]
              (.print writer ENTER-ALT-SCREEN)
              (.print writer CLEAR-SCREEN)
              (.print writer CURSOR-HOME)
              (.print writer HIDE-CURSOR)
              (.flush writer))

            ;; Run main loop
            (main-loop terminal reader server-uri)

            (finally
              ;; Cleanup - exit alternate screen buffer (restores original terminal)
              (let [writer (.writer terminal)]
                (.print writer SHOW-CURSOR)
                (.print writer EXIT-ALT-SCREEN)
                (.flush writer))
              (.close terminal)
              (when-let [client @ws-client]
                (ws/close! client))
              (when-let [ch @msg-chan]
                (close! ch))))))
     (println "Exiting."))))

(defn -main
  "Main entry point."
  [& args]
  (let [server-uri (or (first args) default-server-uri)]
    (run server-uri)))
