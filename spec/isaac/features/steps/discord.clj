(ns isaac.features.steps.discord
  (:require
    [cheshire.core :as json]
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
  (let [cfg    (g/get :discord-config)
        client (gateway/connect! {:token       (get cfg "token")
                                  :clock-mode  :virtual
                                  :connect-ws! (fake-connect! )})]
    (g/assoc! :discord-client client)))

(defwhen gateway-sends-hello "the Gateway sends HELLO:"
  [table]
  (let [payload {:op 10 :d {:heartbeat_interval (parse-value (get (table-map table) "heartbeat_interval"))}}]
    ((:on-message @(g/get :discord-callbacks)) (json/generate-string payload))))

(defwhen gateway-sends-ready "the Gateway sends READY:"
  [table]
  (let [payload {:op 0 :t "READY" :s 1 :d {:session_id (get (table-map table) "session_id")}}]
    ((:on-message @(g/get :discord-callbacks)) (json/generate-string payload))))

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
