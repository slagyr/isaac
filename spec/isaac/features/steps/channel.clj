(ns isaac.features.steps.channel
  (:require
    [gherclj.core :as g :refer [defthen defwhen]]
    [isaac.channel.memory :as memory-channel]
    [isaac.cli.chat.single-turn :as single-turn]
    [isaac.features.matchers :as match]
    [isaac.fs :as fs]
    [isaac.session.storage :as storage]))

(defn- state-dir []
  (g/get :state-dir))

(defn- mem-fs []
  (or (g/get :mem-fs) fs/*fs*))

(defn- with-feature-fs [f]
  (binding [fs/*fs* (mem-fs)]
    (f)))

(defn- channel-send-opts [key-str channel]
  (let [agents     (or (g/get :crew) (g/get :agents))
        models     (g/get :models)
        agent-id   (or (:crew (with-feature-fs #(storage/get-session (state-dir) key-str)))
                       (:agent (with-feature-fs #(storage/get-session (state-dir) key-str)))
                       "main")
        agent-cfg  (get agents agent-id)
        model-cfg  (get models (:model agent-cfg))
        provider   (:provider model-cfg)]
    {:model          (:model model-cfg)
     :soul           (:soul agent-cfg)
     :provider       provider
     :provider-config (get (g/get :provider-configs) provider)
     :context-window (:context-window model-cfg)
     :channel        channel}))

(defwhen user-sends-via-memory-channel "the user sends \"{content:string}\" on session \"{key:string}\" via memory channel"
  [content key-str]
  (let [events  (atom [])
        channel (memory-channel/channel events)
        opts    (channel-send-opts key-str channel)
        result  (atom nil)
        output  (with-out-str
                  (with-feature-fs
                    (fn []
                      (try
                        (reset! result (single-turn/process-user-input! (state-dir) key-str content opts))
                        (catch Exception e
                          (reset! result {:error :exception :message (.getMessage e)}))))))]
    (g/assoc! :current-key key-str)
    (g/assoc! :llm-result @result)
    (g/assoc! :memory-channel-events @events)
    (g/assoc! :output output)))

(defthen memory-channel-events-match "the memory channel has events matching:"
  [table]
  (let [events    (mapv (fn [event]
                          (cond-> event
                            (get-in event [:tool :name]) (assoc :tool-name (get-in event [:tool :name]))))
                        (g/get :memory-channel-events))
        expected  (map #(zipmap (:headers table) %) (:rows table))]
    (loop [remaining events
           expected  expected]
      (if (empty? expected)
        (g/should true)
        (if-let [event (first remaining)]
          (let [row    (mapv #(get (first expected) %) (:headers table))
                result (match/match-entries {:headers (:headers table) :rows [row]} [event])]
            (if (empty? (:failures result))
              (recur (rest remaining) (rest expected))
              (recur (rest remaining) expected)))
          (g/should false))))))
