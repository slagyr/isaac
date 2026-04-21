(ns isaac.comm.discord.gateway
  (:require
    [cheshire.core :as json]
    [isaac.acp.ws :as ws]
    [isaac.logger :as log]))

(def gateway-url "wss://gateway.discord.gg/?v=10&encoding=json")
(def intents 33280)

(defn- normalize-id-set [values]
  (->> (cond
         (nil? values)        []
         (sequential? values) values
         :else                [values])
       (map str)
       set))

(defn- now-ms []
  (System/currentTimeMillis))

(defn- default-connect-ws! [url _handlers]
  (ws/connect! url))

(defn- transport-send! [transport payload]
  (cond
    (:send-payload! transport) ((:send-payload! transport) payload)
    (:send! transport)         ((:send! transport) payload)
    (:send-text! transport)    ((:send-text! transport) (json/generate-string payload))
    :else                      (ws/ws-send! transport (json/generate-string payload))))

(defn- transport-close! [transport]
  (cond
    (:close! transport) ((:close! transport))
    :else               (ws/ws-close! transport)))

(defn- transport-receive! [transport]
  (when-not (:callback-driven? transport)
    (ws/ws-receive! transport 100)))

(defn- identify-payload [token]
  {:op 2
   :d  {:token      token
        :intents    intents
        :properties {:os      (System/getProperty "os.name")
                     :browser "isaac"
                     :device  "isaac"}}})

(defn- heartbeat-payload [sequence]
  {:op 1 :d sequence})

(declare stop!)

(defn- send-identify! [client]
  (let [payload (identify-payload (:token client))]
    (transport-send! (:transport @(:state client)) payload)
    (swap! (:state client) assoc :status :identified)
    (log/info :discord.gateway/identify :intents intents)))

(defn- send-heartbeat! [client]
  (let [payload (heartbeat-payload (:sequence @(:state client)))]
    (transport-send! (:transport @(:state client)) payload)
    (log/debug :discord.gateway/heartbeat :sequence (:d payload))))

(defn- schedule-heartbeats! [client interval-ms]
  (case (:clock-mode client)
    :virtual
    (swap! (:state client) assoc :next-heartbeat-at (+ (:virtual-now-ms @(:state client)) interval-ms))

    (let [runner (future
                   (loop []
                     (Thread/sleep interval-ms)
                     (when (:running? @(:state client))
                       (send-heartbeat! client)
                       (recur))))]
      (swap! (:state client) assoc :heartbeat-runner runner))))

(defn- handle-hello! [client data]
  (let [interval-ms (:heartbeat_interval data)]
    (swap! (:state client) assoc :status :hello-received :heartbeat-interval-ms interval-ms)
    (log/info :discord.gateway/hello :heartbeat-interval-ms interval-ms)
    (schedule-heartbeats! client interval-ms)
    (send-identify! client)))

(defn- handle-dispatch! [client message]
  (when-let [sequence (:s message)]
    (swap! (:state client) assoc :sequence sequence))
  (case (:t message)
    "READY"
    (do
      (swap! (:state client) assoc
             :status     :ready
             :session-id (get-in message [:d :session_id])
             :bot-id     (some-> (get-in message [:d :user :id]) str))
      (log/info :discord.gateway/ready :session-id (get-in message [:d :session_id])))

    "MESSAGE_CREATE"
    (let [payload    (:d message)
          author-id  (some-> (get-in payload [:author :id]) str)
          guild-id   (some-> (:guild_id payload) str)
          bot-id     (:bot-id @(:state client))
          allowed?   (and (contains? (:allow-from-users client) author-id)
                          (contains? (:allow-from-guilds client) guild-id)
                          (not= bot-id author-id))]
      (when allowed?
        (swap! (:state client) update :accepted conj payload)
        (log/debug :discord.gateway/message-accepted
                   :authorId author-id
                   :channelId (:channel_id payload)
                   :guildId guild-id
                   :content (subs (str (:content payload)) 0 (min 80 (count (str (:content payload))))))))

    nil))

(defn- handle-frame! [client message]
  (when-let [sequence (:s message)]
    (swap! (:state client) assoc :sequence sequence))
  (case (:op message)
    10 (handle-hello! client (:d message))
    11 (do
         (swap! (:state client) assoc :last-heartbeat-ack-sequence (:sequence @(:state client)))
         (log/debug :discord.gateway/heartbeat-ack))
    0  (handle-dispatch! client message)
    nil))

(defn receive-text! [client text]
  (try
    (handle-frame! client (json/parse-string text true))
    (catch Exception e
      (log/ex :discord.gateway/invalid-frame e :payload text))))

(defn- on-close! [client payload]
  (swap! (:state client) assoc :status :disconnected :running? false :disconnect payload)
  (log/info :discord.gateway/disconnected :payload payload))

(defn- start-reader-loop! [client transport]
  (when-not (:callback-driven? transport)
    (future
      (loop []
        (when (:running? @(:state client))
          (if-let [message (transport-receive! transport)]
            (do
              (if (map? message)
                (log/error :discord.gateway/transport-error :error (str message))
                (receive-text! client message))
              (recur))
            (on-close! client {:reason "closed"})))))))

(defn connect!
  [{:keys [token url connect-ws! clock-mode allow-from-users allow-from-guilds]
    :or   {url gateway-url connect-ws! default-connect-ws! clock-mode :real}}]
  (let [state      (atom {:status          :disconnected
                          :accepted        []
                          :running?        true
                          :sequence        nil
                          :bot-id          nil
                          :session-id      nil
                          :virtual-now-ms  0
                          :transport       nil})
        client     {:token             token
                    :state             state
                    :clock-mode        clock-mode
                    :allow-from-users  (normalize-id-set allow-from-users)
                    :allow-from-guilds (normalize-id-set allow-from-guilds)}
        handlers   {:on-message #(receive-text! client %)
                    :on-close   #(on-close! client %)
                    :on-error   #(log/error :discord.gateway/error :error (str %))}
        transport  (connect-ws! url handlers)]
    (swap! state assoc :status :connected :transport transport)
    (log/info :discord.gateway/connected :url url)
    (start-reader-loop! client transport)
    client))

(defn advance-time! [client ms]
  (swap! (:state client) update :virtual-now-ms + ms)
  (loop []
    (let [{:keys [heartbeat-interval-ms next-heartbeat-at virtual-now-ms running?]} @(:state client)]
      (when (and running? heartbeat-interval-ms next-heartbeat-at (<= next-heartbeat-at virtual-now-ms))
        (send-heartbeat! client)
        (swap! (:state client) update :next-heartbeat-at + heartbeat-interval-ms)
        (recur)))))

(defn connected? [client]
  (= :ready (:status @(:state client))))

(defn running? [client]
  (true? (:running? @(:state client))))

(defn sequence [client]
  (:sequence @(:state client)))

(defn accepted-messages [client]
  (:accepted @(:state client)))

(defn stop! [client]
  (swap! (:state client) assoc :running? false :status :disconnected)
  (when-let [runner (:heartbeat-runner @(:state client))]
    (future-cancel runner))
  (when-let [transport (:transport @(:state client))]
    (transport-close! transport))
  nil)
