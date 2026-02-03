(ns mdm.isaac.tui.main
  "Main entry point for Isaac terminal client.
   Uses JLine directly for terminal I/O with Elm Architecture pattern."
  (:require [clojure.core.async :as async :refer [chan go-loop <! >! put! close!]]
            [mdm.isaac.tui.auth :as auth]
            [mdm.isaac.tui.core :as core]
            [mdm.isaac.tui.view :as view]
            [mdm.isaac.tui.update :as update]
            [mdm.isaac.tui.ws :as ws])
  (:import [org.jline.terminal TerminalBuilder Terminal]
           [org.jline.utils NonBlockingReader]))

;; Configuration
;; TODO (isaac-lbd) - MDM: use host and port from config to build the URL.
(def default-server-uri "ws://localhost:8600/user/ws")

;; Global state
(defonce ws-client (atom nil))
(defonce msg-chan (atom nil))
(defonce pending-requests (atom {}))
(defonce running (atom false))
(defonce auth-token (atom nil))

;; ANSI escape codes
(def ^:private CLEAR-SCREEN "\u001b[2J")
(def ^:private CURSOR-HOME "\u001b[H")
(def ^:private HIDE-CURSOR "\u001b[?25l")
(def ^:private SHOW-CURSOR "\u001b[?25h")

;; WebSocket handlers

(defn- handle-ws-open []
  (when-let [ch @msg-chan]
    (put! ch {:type :ws-connect}))
  (when-let [client @ws-client]
    (let [goals-req (ws/format-request {:action :goals/list})
          thoughts-req (ws/format-request {:action :thoughts/recent})
          shares-req (ws/format-request {:action :shares/unread})]
      (swap! pending-requests assoc
             (ws/next-request-id!) :goals/list
             (ws/next-request-id!) :thoughts/recent
             (ws/next-request-id!) :shares/unread)
      (ws/send-message! client goals-req)
      (ws/send-message! client thoughts-req)
      (ws/send-message! client shares-req))))

(defn- handle-ws-message [message]
  (when-let [ch @msg-chan]
    (let [[_ action] (first @pending-requests)]
      (swap! pending-requests #(dissoc % (ffirst %)))
      (let [parsed (ws/parse-response (or action :unknown) message)]
        (put! ch parsed)))))

(defn- handle-ws-close [_code _reason]
  (when-let [ch @msg-chan]
    (put! ch {:type :ws-disconnect})))

(defn- handle-ws-error [ex]
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
  (when-let [client @ws-client]
    (when (ws/connected? client)
      (let [parsed (update/parse-command (:text cmd))]
        (when (not= :chat (:action parsed))
          (swap! pending-requests assoc (ws/next-request-id!) (:action parsed))
          (ws/send-message! client (ws/format-request parsed)))))))

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

(defn- render!
  "Renders the view to the terminal."
  [^Terminal terminal state]
  (let [writer (.writer terminal)]
    (.print writer CURSOR-HOME)
    (.print writer CLEAR-SCREEN)
    (.print writer (view/view state))
    (.flush writer)))

(defn- process-ws-messages
  "Process any pending WebSocket messages."
  [state]
  (loop [s state]
    (if-let [ch @msg-chan]
      (if-let [msg (async/poll! ch)]
        (let [[new-s _] (update/update-fn s msg)]
          (recur new-s))
        s)
      s)))

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

  ;; Main loop
  (loop [state (core/init-state server-uri)]
    (when @running
      ;; Process WebSocket messages
      (let [state' (process-ws-messages state)]
        ;; Render
        (render! terminal state')
        ;; Read input
        (if-let [key-event (read-key reader)]
          (let [[new-state cmd] (update/update-fn state' key-event)]
            (cond
              (= :quit cmd)
              (reset! running false)

              (= :send (:type cmd))
              (do
                (send-command! cmd)
                (recur new-state))

              :else
              (recur new-state)))
          ;; No input, just continue loop
          (recur state'))))))

(defn- try-saved-token
  "Tries to use saved token. Returns true if valid token was loaded."
  []
  (when-let [token (auth/load-token)]
    (when (auth/token-valid? token)
      (reset! auth-token token)
      (println "Using saved authentication token...")
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

(defn run
  "Runs the Isaac terminal client."
  ([] (run default-server-uri))
  ([server-uri]
   ;; Authenticate first (before entering raw mode)
   (if (authenticate server-uri)
     (do
       (reset! msg-chan (chan 100))
       (reset! running true)

       (let [terminal (-> (TerminalBuilder/builder)
                          (.system true)
                          (.build))
             reader (.reader terminal)]
         (try
           ;; Enter raw mode
           (.enterRawMode terminal)
           (let [writer (.writer terminal)]
             (.print writer HIDE-CURSOR)
             (.flush writer))

           ;; Run main loop
           (main-loop terminal reader server-uri)

           (finally
             ;; Cleanup
             (let [writer (.writer terminal)]
               (.print writer SHOW-CURSOR)
               (.print writer CLEAR-SCREEN)
               (.print writer CURSOR-HOME)
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
