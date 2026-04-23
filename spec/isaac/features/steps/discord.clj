(ns isaac.features.steps.discord
  (:require
    [babashka.http-client :as http]
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [clojure.string :as str]
    [gherclj.core :as g :refer [defgiven defwhen defthen]]
    [isaac.comm.discord :as discord]
    [isaac.comm.discord.gateway :as gateway]
    [isaac.config.loader :as config]
    [isaac.fs :as fs]
    [isaac.llm.grover :as grover]
    [isaac.session.storage :as storage]))

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
    (= "true" (str/lower-case value)) true
    (= "false" (str/lower-case value)) false
    (re-matches #"-?\d+" value) (parse-long value)
    :else value))

(defn- state-dir []
  (g/get :state-dir))

(defn- mem-fs []
  (or (g/get :mem-fs) fs/*fs*))

(defn- with-feature-fs [f]
  (binding [fs/*fs* (mem-fs)]
    (f)))

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

(defn- loaded-config []
  (when (state-dir)
    (with-feature-fs #(config/load-config {:home (state-dir)}))))

(defn- routing-enabled? []
  (let [cfg (loaded-config)]
    (and (state-dir)
         (seq (or (g/get :crew) (g/get :agents) (:crew cfg)))
         (seq (or (g/get :models) (:models cfg))))))

(defn- discord-cfg-overrides []
  (cond-> {:comms {:discord (current-discord-config)}}
    (seq (g/get :provider-configs))    (assoc :providers (g/get :provider-configs))
    (get (g/get :server-config) :sessions) (assoc :sessions (get (g/get :server-config) :sessions))))

(defn- absolute-path [path]
  (if (str/starts-with? path "/") path (str (state-dir) "/" path)))

(defn- assoc-path [data path value]
  (assoc-in data (str/split path #"\.") value))

(defn- edn-file-data [path]
  (let [path (absolute-path path)]
    (when (fs/exists? path)
      (edn/read-string (fs/slurp path)))))

(defn- route-state [payload]
  (let [route-key (str (get payload :channel_id) "." (get-in payload [:author :id]))
        session   (get-path (edn-file-data "comm/discord/routing.edn") route-key)
        count     (when session
                    (count (or (with-feature-fs #(storage/get-transcript (state-dir) session)) [])))]
    {:count count
     :session session}))

(defn- route-missing? [{:keys [count session]} before]
  (or (nil? session)
      (and count (<= count 1))
      (and (:count before) (= (:count before) count))))

(defn- queue-head []
  (first (gateway/accepted-messages (g/get :discord-client))))

(defn- parse-json-body [body]
  (try
    (json/parse-string body true)
    (catch Exception _
      body)))

(defn- record-request! [method url opts]
  (let [request {:body    (some-> (:body opts) parse-json-body)
                 :headers (:headers opts)
                 :method  method
                 :url     url}]
    (g/assoc! :outbound-http-request request)
    (g/update! :outbound-http-requests #(conj (or % []) request))))

(defn- stubbed-response [url]
  (when-let [stub (get (g/get :url-stubs) url)]
    {:body    (:body stub "")
     :headers (:headers stub {})
     :status  (:status stub 200)}))

(defn- with-http-post-stub [f]
  (with-redefs [http/post (fn [url opts]
                            (record-request! "POST" url opts)
                            (or (stubbed-response url)
                                {:status 200 :headers {} :body "{}"}))]
    (f)))

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
        client (if (state-dir)
                 (:client (with-feature-fs #(discord/connect! {:cfg-overrides   (discord-cfg-overrides)
                                                               :clock-mode      :virtual
                                                               :route-messages? (routing-enabled?)
                                                               :connect-ws!     (fake-connect!)})))
                 (gateway/connect! {:token             (config-value cfg "token")
                                    :allow-from-users  (config-value cfg "allow-from.users")
                                    :allow-from-guilds (config-value cfg "allow-from.guilds")
                                    :clock-mode        :virtual
                                    :connect-ws!       (fake-connect!)}))]
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
                        (table-map table))
        before  (when (routing-enabled?) (with-feature-fs #(route-state payload)))]
    (with-http-post-stub
      (fn []
        (with-feature-fs
          (fn []
            ((:on-message @(g/get :discord-callbacks))
             (json/generate-string {:op 0 :t "MESSAGE_CREATE" :s 2 :d payload}))))
        (when (and (routing-enabled?)
                   (route-missing? (with-feature-fs #(route-state payload)) before))
          (with-feature-fs
            (fn []
              (discord/process-message! (state-dir) payload (discord/config-for (state-dir) (discord-cfg-overrides))))))))
    (g/assoc! :llm-request (grover/last-request))))

(defgiven edn-file-contains "the EDN file \"{path}\" contains:"
  [path table]
  (with-feature-fs
    (fn []
      (let [data      (reduce (fn [acc row]
                                (let [row-map (zipmap (:headers table) row)]
                                  (assoc-path acc (get row-map "path") (parse-value (get row-map "value")))))
                              {}
                              (:rows table))
            full-path (absolute-path path)]
        (fs/mkdirs (fs/parent full-path))
        (fs/spit full-path (pr-str data))))))

(defgiven edn-file-does-not-exist "the EDN file \"{path}\" does not exist"
  [path]
  (with-feature-fs
    #(fs/delete (absolute-path path))))

(defwhen test-clock-advances "the test clock advances {n:int} milliseconds"
  [n]
  (gateway/advance-time! (g/get :discord-client) n))

(defwhen discord-closes-connection "Discord closes the connection with code {n:int}"
  [n]
  ((:on-close @(g/get :discord-callbacks)) {:status n :reason "test-close"}))

(defthen discord-sends-identify "the Discord client sends IDENTIFY:"
  [table]
  (let [message  (sent-op 2)
        expected (into {} (map (fn [[k v]] [k (parse-value v)]) (table-map table)))]
    (g/should-not-be-nil message)
    (g/should= (get expected "token") (get-in message [:d :token]))
    (g/should= (get expected "intents") (get-in message [:d :intents]))))

(defthen discord-sends-resume "the Discord client sends RESUME:"
  [table]
  (let [message  (sent-op 6)
        expected (into {} (map (fn [[k v]] [k (parse-value v)]) (table-map table)))]
    (g/should-not-be-nil message)
    (g/should= (get expected "token") (get-in message [:d :token]))
    (g/should= (get expected "session_id") (get-in message [:d :session_id]))
    (g/should= (get expected "seq") (get-in message [:d :seq]))))

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

(defthen edn-file-matches "the EDN file \"{path}\" matches:"
  [path table]
  (let [data (with-feature-fs #(edn-file-data path))]
    (doseq [row (:rows table)]
      (let [row-map   (zipmap (:headers table) row)
            actual    (get-path data (get row-map "path"))
            expected  (parse-value (get row-map "value"))]
        (g/should= expected actual)))))
