(ns isaac.features.steps.comm
  (:require
    [gherclj.core :as g :refer [defthen defwhen]]
    [isaac.comm.memory :as memory-comm]
    [isaac.drive.turn :as single-turn]
    [isaac.features.matchers :as match]
    [isaac.fs :as fs]
    [isaac.session.storage :as storage]
    [isaac.tool.memory :as memory]))

(defn- state-dir []
  (g/get :state-dir))

(defn- mem-fs []
  (or (g/get :mem-fs) fs/*fs*))

(defn- with-feature-fs [f]
  (binding [fs/*fs* (mem-fs)]
    (f)))

(defn- with-current-time [f]
  (if-let [current-time (g/get :current-time)]
    (binding [memory/*now* current-time]
      (f))
    (f)))

(defn- channel-send-opts [key-str channel]
  (let [cfg        (with-feature-fs #(isaac.config.loader/load-config {:home (state-dir)}))
        agents     (or (:crew cfg) {})
        models     (:models cfg)
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
        channel (memory-comm/channel events)
        opts    (channel-send-opts key-str channel)
        result  (atom nil)
        output  (with-out-str
                  (with-feature-fs
                    (fn []
                      (with-current-time
                        (fn []
                          (try
                            (reset! result (single-turn/process-user-input! (state-dir) key-str content opts))
                            (catch Exception e
                              (reset! result {:error :exception :message (.getMessage e)}))))))))]
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
        expected  (map (fn [row]
                         (into {}
                               (keep (fn [[header value]]
                                       (when (seq value)
                                         [header value]))
                                     (zipmap (:headers table) row))))
                       (:rows table))]
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
