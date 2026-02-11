(ns mdm.isaac.tui.ws
  "WebSocket client for Isaac terminal client.
   Handles connection, message formatting, and response parsing."
  (:require [clojure.edn :as edn])
  (:import [org.java_websocket.client WebSocketClient]
           [java.net URI]))

;; Request ID counter
(def ^:private request-counter (atom 0))

(defn next-request-id!
  "Generates a unique request ID."
  []
  (swap! request-counter inc))

;; Message formatting

(defn format-request
  "Formats a command into a WebSocket request message (EDN string).
   If request-id is provided, uses it; otherwise generates a new one."
  [{:keys [action content query id text request-id]}]
  (let [params (cond-> {}
                 content (assoc :content content)
                 text    (assoc :text text)
                 query   (assoc :query query :limit 10)
                 id      (assoc :id id)
                 (#{:thoughts/recent :thoughts/search} action)
                 (assoc :limit 10))]
    (pr-str {:kind       action
             :params     params
             :request-id (or request-id (next-request-id!))
             :reply?     true})))

;; Response parsing

(defn- extract-error-message
  "Extracts error message from a c3kit response payload.
   Checks :message first, then :flash messages, falls back to 'Server error'."
  [inner]
  (or (:message inner)
      (when-let [flashes (seq (:flash inner))]
        (->> flashes
             (map :text)
             (clojure.string/join "; ")))
      "Server error"))

(defn parse-response
  "Parses a WebSocket response message into an app message.
   Response format from c3kit: {:response-id N :payload {:status :ok/:fail/:error :payload data}}"
  [action response-str]
  (try
    (let [response (edn/read-string response-str)
          inner    (:payload response)]  ;; unwrap c3kit envelope
      (if (= :ok (:status inner))
        {:type    :ws-message
         :action  action
         :payload (:payload inner)}
        {:type    :ws-error
         :action  action
         :message (extract-error-message inner)}))
    (catch Exception e
      {:type    :ws-error
       :action  action
       :message (str "Failed to parse response: " (.getMessage e))})))

;; WebSocket client

(defn create-client!
  "Creates a WebSocket client that connects to the Isaac server.
   on-open, on-message, on-close, on-error are callback functions.
   headers is an optional map of HTTP headers to send with the upgrade request."
  [uri {:keys [on-open on-message on-close on-error headers]}]
  (let [http-headers (java.util.HashMap. (or headers {}))]
    (proxy [WebSocketClient] [(URI. uri) (org.java_websocket.drafts.Draft_6455.) http-headers 10000]
      (onOpen [handshake]
        (when on-open (on-open)))
      (onMessage [message]
        (when on-message (on-message message)))
      (onClose [code reason remote?]
        (when on-close (on-close code reason)))
      (onError [ex]
        (when on-error (on-error ex))))))

(defn connect!
  "Connects the WebSocket client to the server."
  [client]
  (.connect client))

(defn send-message!
  "Sends a message over the WebSocket connection."
  [client message]
  (.send client message))

(defn close!
  "Closes the WebSocket connection."
  [client]
  (.close client))

(defn connected?
  "Returns true if the client is connected."
  [client]
  (.isOpen client))
