(ns isaac.features.steps.discord
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.comm.discord.gateway :as gateway]))

(defn- kv-cells->map [cells]
  (when (and (seq cells) (even? (count cells)))
    (into {} (map (fn [[k v]] [k v]) (partition 2 cells)))))

(defn- table-map [{:keys [headers rows]}]
  (or (let [header-map (kv-cells->map headers)
            row-map    (apply merge {} (keep kv-cells->map rows))]
        (when (or header-map (seq row-map))
          (merge header-map row-map)))
      (when (and (seq headers) (= 1 (count rows)))
        (zipmap headers (first rows)))
      {}))

(defn- parse-value [value]
  (cond
    (nil? value) nil
    (re-matches #"-?\d+" value) (parse-long value)
    :else value))

(defn- get-path [data path]
  (reduce (fn [current segment]
            (cond
              (nil? current) nil
              (map? current) (or (get current (keyword segment))
                                 (get current segment))
              :else nil))
          data
          (str/split path #"\.")))

(defn- config-value [cfg path]
  (get-path cfg path))

(defn- current-discord-config []
  (merge (or (get-in (g/get :server-config) [:comms :discord]) {})
         (or (g/get :discord-config) {})))

(defn- queue-head []
  (first (gateway/accepted-messages (g/get :discord-client))))

(defn- fake-connect! []
  (let [sent       (or (g/get :discord-sent) (atom []))
        callbacks* (or (g/get :discord-callbacks) (atom nil))]
    (g/assoc! :discord-sent sent)
    (g/assoc! :discord-callbacks callbacks*)
    (fn [_url callbacks]
      (reset! callbacks* callbacks)
      {:callback-driven? true
       :close!           (fn [] nil)
       :send-payload!    (fn [payload] (swap! sent conj payload))})))

(defn- sent-op [op]
  (some #(when (= op (:op %)) %) @(g/get :discord-sent)))

(defgiven discord-faked "the Discord Gateway is faked in-memory"
  []
  (g/assoc! :discord-sent (atom []))
  (g/assoc! :discord-callbacks (atom nil)))

(defgiven discord-configured "Discord is configured with:"
  [table]
  (g/assoc! :discord-config (into {} (map (fn [[k v]] [k (parse-value v)]) (table-map table)))))

(defwhen discord-connects "the Discord client connects"
  []
  (let [cfg    (current-discord-config)
        client (gateway/connect! {:token             (config-value cfg "token")
                                  :allow-from-users  (config-value cfg "allow-from.users")
                                  :allow-from-guilds (config-value cfg "allow-from.guilds")
                                  :clock-mode  :virtual
                                  :connect-ws! (fake-connect! )})]
    (g/assoc! :discord-client client)))

(defn- ensure-connected! []
  (when-not (g/get :discord-client)
    (discord-connects)))

(defwhen gateway-sends-hello "the Gateway sends HELLO:"
  [table]
  (let [payload {:op 10 :d {:heartbeat_interval (parse-value (get (table-map table) "heartbeat_interval"))}}]
    ((:on-message @(g/get :discord-callbacks)) (json/generate-string payload))))

(defwhen discord-sends-hello "Discord sends HELLO:"
  [table]
  (gateway-sends-hello table))

(defwhen gateway-sends-ready "the Gateway sends READY:"
  [table]
  (let [payload {:op 0 :t "READY" :s 1 :d {:session_id (get (table-map table) "session_id")}}]
    ((:on-message @(g/get :discord-callbacks)) (json/generate-string payload))))

(defwhen discord-sends-ready "Discord sends READY:"
  [table]
  (gateway-sends-ready table))

(defgiven discord-client-ready-as-bot #"the Discord client is ready as bot \"([^\"]+)\""
  [bot-id]
  (ensure-connected!)
  (gateway-sends-hello {:headers ["heartbeat_interval" "45000"] :rows []})
  ((:on-message @(g/get :discord-callbacks))
   (json/generate-string {:op 0 :t "READY" :s 1 :d {:session_id "fake-session" :user {:id bot-id}}})))

(defwhen discord-sends-message-create "Discord sends MESSAGE_CREATE:"
  [table]
  (let [payload (reduce (fn [acc [k v]]
                          (assoc-in acc (mapv keyword (clojure.string/split k #"\.")) (parse-value v)))
                        {}
                        (table-map table))]
    ((:on-message @(g/get :discord-callbacks))
     (json/generate-string {:op 0 :t "MESSAGE_CREATE" :s 2 :d payload}))))

(defwhen test-clock-advances "the test clock advances {n:int} milliseconds"
  [n]
  (gateway/advance-time! (g/get :discord-client) n))

(defthen discord-sends-identify "the Discord client sends IDENTIFY:"
  [table]
  (let [message  (sent-op 2)
        expected (into {} (map (fn [[k v]] [k (parse-value v)]) (table-map table)))]
    (g/should-not-be-nil message)
    (g/should= (get expected "token") (get-in message [:d :token]))
    (g/should= (get expected "intents") (get-in message [:d :intents]))))

(defthen discord-sends-heartbeat "the Discord client sends HEARTBEAT"
  []
  (g/should-not-be-nil (sent-op 1)))

(defthen discord-client-connected "the Discord client is connected"
  []
  (g/should (gateway/connected? (g/get :discord-client))))

(defthen discord-client-accepted-message "the Discord client accepted a message with:"
  [table]
  (let [message  (queue-head)
        expected (into {} (map (fn [[k v]] [k (parse-value v)]) (table-map table)))]
    (g/should-not-be-nil message)
    (doseq [[k v] expected]
      (g/should= v (get-path message k)))))

(defthen discord-client-accepted-no-messages "the Discord client accepted no messages"
  []
  (g/should= [] (gateway/accepted-messages (g/get :discord-client))))
