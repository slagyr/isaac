(ns isaac.features.steps.comm
  (:require
    [gherclj.core :as g :refer [defthen defwhen helper!]]
    [isaac.comm.memory :as memory-comm]
    [isaac.config.loader :as config]
    [isaac.drive.dispatch :as dispatch]
    [isaac.features.matchers :as match]
    [isaac.fs :as fs]
    [isaac.llm.api.grover :as grover]
    [isaac.llm.http :as llm-http]
    [isaac.session.store :as store]
    [isaac.session.store.file :as file-store]
    [isaac.tool.memory :as memory]))

(helper! isaac.features.steps.comm)

(defn- state-dir []
  (g/get :state-dir))

(defn- mem-fs []
  (or (g/get :mem-fs) fs/*fs*))

(defn- with-feature-fs [f]
  (binding [fs/*fs* (mem-fs)]
    (f)))

(defn- session-store []
  (file-store/create-store (state-dir)))

(defn- get-session [session-key]
  (store/get-session (session-store) session-key))

(defn- with-current-time [f]
  (if-let [current-time (g/get :current-time)]
    (binding [memory/*now* current-time]
      (f))
    (f)))

(defn- channel-send-opts [key-str channel]
  (let [cfg        (with-feature-fs #(config/load-config {:home (state-dir)}))
        agents     (or (:crew cfg) {})
        models     (:models cfg)
        agent-id   (or (:crew (with-feature-fs #(get-session key-str)))
                       (:agent (with-feature-fs #(get-session key-str)))
                       "main")
        agent-cfg  (get agents agent-id)
        model-id   (:model agent-cfg)
        model-cfg  (or (get models model-id)
                       (when-let [provider-id (:provider agent-cfg)]
                         {:model model-id :provider provider-id}))
        provider   (:provider model-cfg)
        prov-cfg   (merge (or (config/resolve-provider cfg provider) {})
                          (or (get (g/get :provider-configs) provider) {}))]
    {:model          (:model model-cfg)
     :crew-members   agents
     :models         models
     :soul           (:soul agent-cfg)
     :provider       (when provider (dispatch/make-provider provider prov-cfg))
     :context-window (:context-window model-cfg)
     :comm           channel}))

(defn user-sends-via-memory-channel [content key-str]
  (grover/clear-provider-requests!)
  (llm-http/clear-outbound-requests!)
  (let [events            (atom [])
        channel           (memory-comm/channel events)
        cfg               (with-feature-fs #(config/load-config {:home (state-dir)}))
        _                 (with-feature-fs #(store/open-session! (session-store) key-str {}))
        opts              (channel-send-opts key-str channel)
        result            (atom nil)
        output            (with-out-str
                            (with-feature-fs
                              (fn []
                                (with-current-time
                                  (fn []
                                    (try
                                      (config/set-snapshot! cfg)
                                      (reset! result ((requiring-resolve 'isaac.bridge.core/dispatch!)
                                                      (state-dir)
                                                      (assoc opts :input content :session-key key-str)))
                                      (catch Exception e
                                        (reset! result {:error :exception :message (.getMessage e)}))))))))
        outbound-requests (or (seq (llm-http/outbound-requests))
                              (seq (grover/provider-requests)))
        outbound-requests (some-> outbound-requests vec)]
    (g/assoc! :current-key key-str)
    (g/assoc! :llm-result @result)
    (g/assoc! :provider-request (or (last outbound-requests)
                                    (grover/last-provider-request)))
    (g/assoc! :outbound-http-requests outbound-requests)
    (g/assoc! :outbound-http-request (or (first outbound-requests)
                                         (grover/last-provider-request)))
    (g/assoc! :memory-comm-events @events)
    (g/assoc! :output output)))

(defn memory-channel-events-match [table]
  (let [events    (mapv (fn [event]
                          (cond-> event
                            (get-in event [:tool :name]) (assoc :tool-name (get-in event [:tool :name]))))
                        (g/get :memory-comm-events))
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

(defwhen "the user sends \"{content:string}\" on session \"{key:string}\" via memory comm" comm/user-sends-via-memory-channel)

(defthen "the memory comm has events matching:" comm/memory-channel-events-match
  "Reads :memory-comm-events captured by the preceding 'via memory
   comm' When step. Matches rows as an in-order subsequence — extra
   events between matched rows are allowed.")
