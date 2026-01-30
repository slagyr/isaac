(ns mdm.isaac.client.main
  "Main entry point for Isaac terminal client.
   Integrates charm.clj TUI with WebSocket connection to Isaac server."
  (:require [charm.core :as charm]
            [clojure.core.async :as async :refer [go <! >! chan]]
            [mdm.isaac.client.core :as core]
            [mdm.isaac.client.view :as view]
            [mdm.isaac.client.update :as update]
            [mdm.isaac.client.ws :as ws]))

;; Configuration
(def default-server-uri "ws://localhost:8080/ws")

;; Global state for WebSocket client and message channel
(defonce ws-client (atom nil))
(defonce msg-chan (atom nil))

;; Pending requests - maps request-id to action type
(defonce pending-requests (atom {}))

(defn- handle-ws-open []
  (when-let [ch @msg-chan]
    (async/put! ch {:type :ws-connect}))
  ;; Request initial data
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
    ;; For simplicity, assume responses come in order
    ;; In production, would use request IDs
    (let [[_ action] (first @pending-requests)]
      (swap! pending-requests #(dissoc % (ffirst %)))
      (let [parsed (ws/parse-response (or action :unknown) message)]
        (async/put! ch parsed)))))

(defn- handle-ws-close [_code _reason]
  (when-let [ch @msg-chan]
    (async/put! ch {:type :ws-disconnect})))

(defn- handle-ws-error [ex]
  (when-let [ch @msg-chan]
    (async/put! ch {:type :ws-error :message (.getMessage ex)})))

(defn- connect-websocket!
  "Establishes WebSocket connection to Isaac server."
  [uri]
  (let [client (ws/create-client! uri
                                  {:on-open    handle-ws-open
                                   :on-message handle-ws-message
                                   :on-close   handle-ws-close
                                   :on-error   handle-ws-error})]
    (reset! ws-client client)
    (ws/connect! client)))

(defn- send-command!
  "Sends a command to the server via WebSocket."
  [cmd]
  (when-let [client @ws-client]
    (when (ws/connected? client)
      (let [parsed (update/parse-command (:text cmd))]
        (when (not= :chat (:action parsed))
          (swap! pending-requests assoc (ws/next-request-id!) (:action parsed))
          (ws/send-message! client (ws/format-request parsed)))))))

;; Charm.clj integration

(defn- key-match?
  "Checks if message matches a key press."
  [msg key]
  (and (= :key-press (:type msg))
       (= key (:key msg))))

(defn- translate-charm-message
  "Translates charm.clj message format to our internal format."
  [charm-msg]
  (cond
    (charm/key-press? charm-msg)
    {:type :key-press
     :key  (or (:key charm-msg)
               (:rune charm-msg)
               (when (:runes charm-msg)
                 (first (:runes charm-msg))))}

    :else
    {:type :unknown :original charm-msg}))

(defn- charm-update
  "Update function for charm.clj - bridges to our update-fn."
  [state msg]
  ;; Check for WebSocket messages from channel
  (let [ws-msgs (loop [msgs []]
                  (if-let [ch @msg-chan]
                    (let [[msg _] (async/poll! ch)]
                      (if msg
                        (recur (conj msgs msg))
                        msgs))
                    msgs))
        ;; Apply WebSocket messages first
        state' (reduce (fn [s m]
                         (let [[new-s _] (update/update-fn s m)]
                           new-s))
                       state
                       ws-msgs)
        ;; Then apply charm message
        internal-msg (translate-charm-message msg)
        [new-state cmd] (update/update-fn state' internal-msg)]
    (cond
      (= :quit cmd)
      [new-state charm/quit-cmd]

      (= :send (:type cmd))
      (do
        (send-command! cmd)
        [new-state nil])

      :else
      [new-state nil])))

(defn- charm-view
  "View function for charm.clj - calls our view function."
  [state]
  (view/view state))

(defn- init-state
  "Creates initial state, or returns function for init with size."
  []
  (core/init-state))

(defn run
  "Runs the Isaac terminal client."
  ([] (run default-server-uri))
  ([server-uri]
   ;; Initialize message channel
   (reset! msg-chan (chan 100))

   ;; Connect to WebSocket in background
   (future
     (Thread/sleep 500) ;; Give charm time to start
     (try
       (connect-websocket! server-uri)
       (catch Exception e
         (when-let [ch @msg-chan]
           (async/put! ch {:type :ws-error
                           :message (str "Connection failed: " (.getMessage e))})))))

   ;; Run charm.clj TUI
   (try
     (charm/run {:init      init-state
                 :update    charm-update
                 :view      charm-view
                 :alt-screen true
                 :fps       30})
     (finally
       ;; Cleanup
       (when-let [client @ws-client]
         (ws/close! client))
       (when-let [ch @msg-chan]
         (async/close! ch))))))

(defn -main
  "Main entry point."
  [& args]
  (let [server-uri (or (first args) default-server-uri)]
    (run server-uri)))
