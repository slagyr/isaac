(ns isaac.acp.ws
  (:require
    [clojure.string :as str])
  (:import
    (java.net URI)
    (java.net.http HttpClient WebSocket WebSocket$Listener)
    (java.util.concurrent CompletableFuture LinkedBlockingQueue TimeUnit)))

(def ^:private closed-sentinel ::closed)

(defprotocol WsConnection
  (ws-send! [this message])
  (ws-receive! [this] [this timeout-ms])
  (ws-close! [this]))

(defn- completed-future []
  (CompletableFuture/completedFuture nil))

(defn- queue-message! [queue message]
  (.put queue message))

(defn- queue-closed! [queue]
  (.offer queue closed-sentinel))

(defn- receive-queue-message [queue closed? timeout-ms]
  (let [message (if (some? timeout-ms)
                  (.poll queue timeout-ms TimeUnit/MILLISECONDS)
                  (.take queue))]
    (cond
      (= closed-sentinel message) nil
      (and (nil? message) @closed?) nil
      :else message)))

(deftype LoopbackWs [incoming outgoing closed?]
  WsConnection
  (ws-send! [_ message]
    (when-not @closed?
      (queue-message! outgoing message))
    nil)
  (ws-receive! [_]
    (receive-queue-message incoming closed? nil))
  (ws-receive! [_ timeout-ms]
    (receive-queue-message incoming closed? timeout-ms))
  (ws-close! [_]
    (reset! closed? true)
    (queue-closed! incoming)
    (queue-closed! outgoing)
    nil))

(defn loopback-pair []
  (let [client-incoming (LinkedBlockingQueue.)
        server-incoming (LinkedBlockingQueue.)
        client-closed?  (atom false)
        server-closed?  (atom false)]
    {:client (->LoopbackWs client-incoming server-incoming client-closed?)
     :server (->LoopbackWs server-incoming client-incoming server-closed?)}))

(defrecord ReconnectableLoopback [accept-queue active-client active-server dropped? permanent?])

(defn reconnectable-loopback []
  (->ReconnectableLoopback (LinkedBlockingQueue.) (atom nil) (atom nil) (atom false) (atom false)))

(defn connect-loopback! [transport _url]
  (when @(:permanent? transport)
    (throw (ex-info "loopback unavailable" {:type :loopback/unavailable})))
  (when @(:dropped? transport)
    (throw (ex-info "loopback dropped" {:type :loopback/dropped})))
  (let [{:keys [client server]} (loopback-pair)]
    (reset! (:active-client transport) client)
    (reset! (:active-server transport) server)
    (.put ^LinkedBlockingQueue (:accept-queue transport) server)
    client))

(defn accept-loopback! [transport]
  (.poll ^LinkedBlockingQueue (:accept-queue transport) 1000 TimeUnit/MILLISECONDS))

(defn drop-loopback! [transport]
  (reset! (:dropped? transport) true)
  (some-> @(:active-client transport) ws-close!)
  (some-> @(:active-server transport) ws-close!)
  nil)

(defn restore-loopback! [transport]
  (reset! (:dropped? transport) false)
  (reset! (:active-client transport) nil)
  (reset! (:active-server transport) nil)
  nil)

(defn drop-loopback-permanently! [transport]
  (reset! (:dropped? transport) true)
  (reset! (:permanent? transport) true)
  (some-> @(:active-client transport) ws-close!)
  (some-> @(:active-server transport) ws-close!)
  nil)

(deftype RealWs [websocket incoming closed?]
  WsConnection
  (ws-send! [_ message]
    (.join (.sendText websocket message true))
    nil)
  (ws-receive! [_]
    (receive-queue-message incoming closed? nil))
  (ws-receive! [_ timeout-ms]
    (receive-queue-message incoming closed? timeout-ms))
  (ws-close! [_]
    (reset! closed? true)
    (.join (.sendClose websocket WebSocket/NORMAL_CLOSURE "bye"))
    (queue-closed! incoming)
    nil))

(defn connect!
  ([url]
   (connect! url {}))
  ([url {:keys [headers]}]
   (let [incoming (LinkedBlockingQueue.)
         closed?  (atom false)
         partial  (StringBuilder.)
         listener (reify WebSocket$Listener
                    (onOpen [_ websocket]
                      (.request websocket 1)
                      (completed-future))
                    (onText [_ websocket data last?]
                      (locking partial
                        (.append partial data)
                        (when last?
                          (queue-message! incoming (.toString partial))
                          (.setLength partial 0)))
                      (.request websocket 1)
                      (completed-future))
                    (onBinary [_ websocket _data _last?]
                      (.request websocket 1)
                      (completed-future))
                    (onClose [_ _websocket _status-code _reason]
                      (reset! closed? true)
                      (queue-closed! incoming)
                      (completed-future))
                    (onError [_ _websocket error]
                      (reset! closed? true)
                      (queue-message! incoming {:error error})
                      nil))
         builder  (.newWebSocketBuilder (HttpClient/newHttpClient))]
     (doseq [[header value] headers]
       (.header builder header value))
     (let [websocket (.join (.buildAsync builder (URI/create url) listener))]
       (->RealWs websocket incoming closed?)))))

(defn written-lines [writer]
  (->> (str/split-lines (str writer))
       (remove str/blank?)
       vec))
