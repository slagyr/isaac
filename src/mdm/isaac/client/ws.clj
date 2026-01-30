(ns mdm.isaac.client.ws
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
  "Formats a command into a WebSocket request message (EDN string)."
  [{:keys [action content query id]}]
  (let [params (cond-> {}
                 content (assoc :content content)
                 query   (assoc :query query :limit 10)
                 id      (assoc :id id)
                 (#{:thoughts/recent :thoughts/search} action)
                 (assoc :limit 10))]
    (pr-str {:kind action :params params})))

;; Response parsing

(defn parse-response
  "Parses a WebSocket response message into an app message."
  [action response-str]
  (try
    (let [response (edn/read-string response-str)]
      (if (= :ok (:status response))
        {:type    :ws-message
         :action  action
         :payload (:payload response)}
        {:type    :ws-error
         :action  action
         :message (:message response "Unknown error")}))
    (catch Exception e
      {:type    :ws-error
       :action  action
       :message (str "Failed to parse response: " (.getMessage e))})))

;; WebSocket client

(defn create-client!
  "Creates a WebSocket client that connects to the Isaac server.
   on-open, on-message, on-close, on-error are callback functions."
  [uri {:keys [on-open on-message on-close on-error]}]
  (proxy [WebSocketClient] [(URI. uri)]
    (onOpen [handshake]
      (when on-open (on-open)))
    (onMessage [message]
      (when on-message (on-message message)))
    (onClose [code reason remote?]
      (when on-close (on-close code reason)))
    (onError [ex]
      (when on-error (on-error ex)))))

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
