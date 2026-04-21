(ns isaac.comm.discord
  (:require
    [clojure.edn :as edn]
    [isaac.comm :as comm]
    [isaac.comm.discord.gateway :as gateway]
    [isaac.config.loader :as config]
    [isaac.drive.turn :as turn]
    [isaac.fs :as fs]
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
  (merge-config (config/load-config {:home state-dir}) overrides))

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
  (:name (storage/create-session! state-dir nil {:agent    crew-id
                                                 :channel  "discord"
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

(deftype DiscordComm []
  comm/Comm
  (on-turn-start [_ _ _] nil)
  (on-text-chunk [_ _ _] nil)
  (on-tool-call [_ _ _] nil)
  (on-tool-cancel [_ _ _] nil)
  (on-tool-result [_ _ _ _] nil)
  (on-turn-end [_ _ _] nil)
  (on-error [_ _ _] nil))

(defn channel []
  (->DiscordComm))

(defn- turn-options [cfg crew-id]
  (let [{:keys [context-window model provider provider-config soul]} (config/resolve-crew-context cfg crew-id)]
    {:channel         (channel)
     :context-window  context-window
     :crew-members    (:crew cfg)
     :model           model
     :models          (:models cfg)
     :provider        provider
     :provider-config provider-config
     :soul            soul}))

(defn- routing-configured? [cfg]
  (and (seq (:crew cfg))
       (seq (:models cfg))))

(defn process-message!
  ([state-dir payload]
   (process-message! state-dir payload nil))
  ([state-dir payload cfg]
   (let [cfg          (or cfg (effective-config state-dir nil))
        crew-id      (or (->id (:crew (discord-config cfg))) "main")
        session-name (ensure-session! state-dir crew-id payload)
        input        (or (:content payload) "")]
     (with-out-str
       (turn/process-user-input! state-dir session-name input (turn-options cfg crew-id))))))

(defn connect!
  [{:keys [cfg-overrides clock-mode connect-ws! route-messages? state-dir url]}]
  (let [cfg         (effective-config state-dir cfg-overrides)
        discord-cfg (discord-config cfg)
        client      (gateway/connect! (cond-> {:allow-from-guilds (get-in discord-cfg [:allow-from :guilds])
                                               :allow-from-users  (get-in discord-cfg [:allow-from :users])
                                               :token             (:token discord-cfg)}
                                        (if (some? route-messages?) route-messages? (routing-configured? cfg))
                                        (assoc :on-accepted-message! #(process-message! state-dir % cfg))
                                        clock-mode (assoc :clock-mode clock-mode)
                                        connect-ws! (assoc :connect-ws! connect-ws!)
                                        url (assoc :url url)))]
    {:client client}))
