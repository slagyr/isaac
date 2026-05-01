(ns isaac.comm.discord
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [isaac.comm :as comm]
    [isaac.comm.discord.gateway :as gateway]
    [isaac.comm.discord.rest :as rest]
    [isaac.config.loader :as config]
    [isaac.drive.turn :as turn]
    [isaac.fs :as fs]
    [isaac.logger :as log]
    [isaac.plugin :as plugin]
    [isaac.session.storage :as storage]))

(defn- ->id [value]
  (cond
    (keyword? value) (name value)
    (some? value)    (str value)
    :else            nil))

(defn- discord-config [cfg]
  (merge (or (get-in cfg [:channels :discord]) {})
         (or (get-in cfg [:comms :discord]) {})))

(defn- merge-config [base overrides]
  (cond-> base
    (:channels overrides)  (update :channels merge (:channels overrides))
    (:comms overrides)     (update :comms merge (:comms overrides))
    (:crew overrides)      (assoc :crew (:crew overrides))
    (:models overrides)    (assoc :models (:models overrides))
    (:providers overrides) (assoc :providers (:providers overrides))
    (:sessions overrides)  (assoc :sessions (:sessions overrides))))

(defn- effective-config [state-dir overrides]
  (merge-config (if state-dir
                  (config/load-config {:home (fs/parent state-dir)})
                  {})
                overrides))

(defn config-for [state-dir overrides]
  (effective-config state-dir overrides))

(defn routing-path [state-dir]
  (str state-dir "/comm/discord/routing.edn"))

(defn- read-routing-table [state-dir]
  (let [path (routing-path state-dir)]
    (if (fs/exists? path)
      (or (edn/read-string (fs/slurp path)) {})
      {})))

(defn- write-routing-table! [state-dir routing]
  (let [path (routing-path state-dir)]
    (fs/mkdirs (fs/parent path))
    (fs/spit path (pr-str routing))))

(defn- route-path [{:keys [channel_id author]}]
  [(->id channel_id) (->id (:id author))])

(defn- route-session-name [routing payload]
  (get-in routing (route-path payload)))

(defn- create-session! [state-dir crew-id]
  (:name (storage/create-session! state-dir nil {:channel  "discord"
                                                 :chatType "direct"
                                                 :crew     crew-id
                                                 :cwd      state-dir})))

(defn- ensure-session! [state-dir crew-id payload]
  (let [routing      (read-routing-table state-dir)
        session-name (route-session-name routing payload)]
    (if session-name
      session-name
      (let [session-name (create-session! state-dir crew-id)]
        (write-routing-table! state-dir (assoc-in routing (route-path payload) session-name))
        session-name))))

(defn- result-content [result]
  (or (:content result)
      (get-in result [:response :message :content])
      ""))

(defn- session->channel-id [state-dir session-name]
  (some (fn [[channel-id users]]
          (when (some (fn [[_uid sn]] (= session-name sn)) users)
            channel-id))
        (read-routing-table state-dir)))

(declare connect!)

(deftype DiscordIntegration [state-dir connect-ws! cfg-atom conn]
  comm/Comm
  (on-turn-start [_ session-key _]
    (let [cfg @cfg-atom]
      (when-let [channel-id (session->channel-id state-dir session-key)]
        (rest/post-typing! {:channel-id channel-id :token (:token cfg)}))))
  (on-text-chunk [_ _ _] nil)
  (on-tool-call [_ _ _] nil)
  (on-tool-cancel [_ _ _] nil)
  (on-tool-result [_ _ _ _] nil)
  (on-compaction-start [_ _ _] nil)
  (on-compaction-success [_ _ _] nil)
  (on-compaction-failure [_ _ _] nil)
  (on-compaction-disabled [_ _ _] nil)
  (on-turn-end [_ session-key result]
    (let [cfg     @cfg-atom
          content (some-> (result-content result) str/trim)]
      (when (seq content)
        (when-let [channel-id (session->channel-id state-dir session-key)]
          (rest/try-send-or-enqueue! {:channel-id  channel-id
                                      :content     content
                                      :message-cap (:message-cap cfg)
                                      :state-dir   state-dir
                                      :token       (:token cfg)})))))
  (on-error [_ _ _] nil)

  plugin/Plugin
  (config-path [_] [:comms :discord])
  (on-startup! [this cfg]
    (reset! cfg-atom cfg)
    (when-let [token (:token cfg)]
      (when state-dir
        (let [result (connect! (cond-> {:cfg-overrides {:comms {:discord cfg}}
                                        :comm-impl     this
                                        :state-dir     state-dir}
                                  connect-ws! (assoc :connect-ws! connect-ws!)))]
          (reset! conn {:client (:client result)})))))
  (on-config-change! [this old new]
    (when new (reset! cfg-atom new))
    (let [old-token (:token old)
          new-token (:token new)]
      (cond
        (and (not old-token) new-token state-dir)
        (do
          (let [result (connect! (cond-> {:cfg-overrides {:comms {:discord new}}
                                          :comm-impl     this
                                          :state-dir     state-dir}
                                   connect-ws! (assoc :connect-ws! connect-ws!)))]
            (reset! conn {:client (:client result)}))
          (log/info :discord.client/started))

        (and old-token (not new-token))
        (when-let [current @conn]
          (gateway/stop! (:client current))
          (reset! conn nil)
          (log/info :discord.client/stopped))

        (and old-token new-token)
        (when-let [current @conn]
          (gateway/update-allow-from! (:client current)
                                      {:allow-from-users  (get-in new [:allow-from :users])
                                       :allow-from-guilds (get-in new [:allow-from :guilds])}))))))

(defn- turn-options [cfg crew-id channel-impl]
  (let [{:keys [context-window model provider soul]} (config/resolve-crew-context cfg crew-id)]
    {:channel        channel-impl
     :context-window context-window
     :crew-members   (:crew cfg)
     :model          model
     :models         (:models cfg)
     :provider       provider
     :soul           soul}))

(defn- routing-configured? [cfg]
  (and (seq (:crew cfg))
       (seq (:models cfg))))

(defn process-message!
  ([state-dir payload]
   (process-message! nil state-dir payload))
  ([comm-impl state-dir payload]
   (let [cfg          (effective-config state-dir nil)
         crew-id      (or (->id (:crew (discord-config cfg))) "main")
         session-name (ensure-session! state-dir crew-id payload)
         input        (or (:content payload) "")]
     (with-out-str
       (turn/run-turn! state-dir session-name input (turn-options cfg crew-id comm-impl))))))

(defn connect!
  [{:keys [cfg-overrides clock-mode comm-impl connect-ws! route-messages? state-dir url]}]
  (let [cfg         (effective-config state-dir cfg-overrides)
        discord-cfg (discord-config cfg)
        routing?    (if (some? route-messages?) route-messages? (routing-configured? cfg))
        di          (or comm-impl
                        (when routing?
                          (->DiscordIntegration state-dir connect-ws! (atom discord-cfg) (atom nil))))
        client      (gateway/connect! (cond-> {:allow-from-guilds (get-in discord-cfg [:allow-from :guilds])
                                               :allow-from-users  (get-in discord-cfg [:allow-from :users])
                                               :token             (:token discord-cfg)}
                                        (some? di)  (assoc :on-accepted-message! #(process-message! di state-dir %))
                                        clock-mode  (assoc :clock-mode clock-mode)
                                        connect-ws! (assoc :connect-ws! connect-ws!)
                                        url         (assoc :url url)))
        _           (when (and di (nil? comm-impl))
                      (reset! (.-conn di) {:client client}))]
    {:client      client
     :integration di}))

(defn integration [ctx]
  (->DiscordIntegration (:state-dir ctx) (:connect-ws! ctx) (atom nil) (atom nil)))

(defn discord-integration? [value]
  (instance? DiscordIntegration value))

(defn client [di]
  (some-> di .-conn deref))

(defonce _plugin-registration
  (plugin/register! integration))
