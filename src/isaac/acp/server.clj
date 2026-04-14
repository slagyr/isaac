(ns isaac.acp.server
  (:require
    [isaac.acp.rpc :as rpc]
    [isaac.channel.acp :as acp-channel]
    [isaac.cli.chat.single-turn :as single-turn]
    [isaac.config.resolution :as config]
    [isaac.session.bridge :as bridge]
    [isaac.session.key :as key]
    [isaac.session.storage :as storage]))

(def ^:private startup-cwd (System/getProperty "user.dir"))

(defonce ^:private interrupts (atom {}))

(defn- interrupt! [session-id]
  (swap! interrupts assoc session-id true))

(defn- interrupted? [session-id]
  (get @interrupts session-id false))

(defn- clear-interrupt! [session-id]
  (swap! interrupts dissoc session-id))

(defn- with-startup-cwd [f]
  (let [original (System/getProperty "user.dir")]
    (try
      (when-not (= startup-cwd original)
        (System/setProperty "user.dir" startup-cwd))
      (f)
      (finally
        (when-not (= startup-cwd original)
          (System/setProperty "user.dir" original))))))

(defn- session-new-handler [state-dir agent-id params _message]
  (let [session (with-startup-cwd #(storage/create-session! state-dir (:name params) {:crew agent-id :agent agent-id :channel "acp" :chatType "direct"}))]
    {:sessionId (:id session)}))

(defn- session-load-handler [state-dir _agent-id params _message]
  (if-let [session (storage/open-session state-dir (:sessionId params))]
    {:sessionId (:id session)}
    (throw (ex-info (str "session not found: " (:sessionId params)) {:code -32602}))))

(defn- initialize-result [model provider]
  {:protocolVersion   1
   :agentInfo         (cond-> {:name "isaac" :version "dev"}
                        model    (assoc :model model)
                        provider (assoc :provider provider))
   :agentCapabilities {:loadSession true
                       :promptCapabilities {:text true}}})

(defn- resolve-agent-model [agents models provider-configs cfg home model-override agent-id]
  (let [lookup-model (fn [model-key]
                       (or (get models model-key)
                           (get models (keyword model-key))))]
    (if cfg
      (if model-override
        (let [ctx         (config/resolve-crew-context cfg agent-id {:home home})
              alias-match (get-in cfg [:agents :models (keyword model-override)])
              parsed      (when-not alias-match (config/parse-model-ref model-override))
              provider    (or (:provider alias-match) (:provider parsed))
              provider-cfg (when provider (config/resolve-provider cfg provider))]
          (if (or alias-match parsed)
            (assoc ctx
              :model           (or (:model alias-match) (:model parsed))
              :provider        provider
              :context-window  (or (:contextWindow alias-match)
                                   (:contextWindow provider-cfg)
                                   (:context-window ctx)
                                   32768)
              :provider-config (or provider-cfg {}))
            ctx))
        (config/resolve-crew-context cfg agent-id {:home home}))
      (let [agent-cfg   (get agents agent-id)
            model-alias (or model-override (:model agent-cfg))
            model-cfg   (lookup-model model-alias)]
        {:soul            (:soul agent-cfg)
         :model           (:model model-cfg)
         :provider        (:provider model-cfg)
         :context-window  (:contextWindow model-cfg)
         :provider-config (or (get provider-configs (:provider model-cfg)) {})}))))

(defn- initialize-handler [opts _params _message]
  (let [{:keys [agents models provider-configs cfg home agent-id model-override] :or {agent-id "main"}} opts
        {:keys [model provider]} (resolve-agent-model (or agents {}) (or models {}) (or provider-configs {}) cfg home model-override agent-id)]
    (initialize-result model provider)))

(defn- prompt->text [prompt]
  (->> (or prompt [])
       (filter #(= "text" (:type %)))
       first
       :text))

(defn- session-cancel-handler [params _message]
  (let [session-id (get params :sessionId)]
    (interrupt! session-id)
    nil))

(defn- run-turn [state-dir output-writer session-id text soul model provider provider-config context-window]
  (if (interrupted? session-id)
    (do
      (clear-interrupt! session-id)
      {:stopReason "cancelled"})
    (let [channel     (acp-channel/channel output-writer)
          turn-result (atom nil)]
      (with-out-str
        (reset! turn-result
                (with-startup-cwd
                  #(single-turn/process-user-input! state-dir session-id text
                                                    {:model           model
                                                     :crew-members    agents
                                                     :soul            soul
                                                     :provider        provider
                                                     :provider-config provider-config
                                                     :context-window  context-window
                                                     :channel         channel}))))
      (if (:error @turn-result)
        {:stopReason "error" :error (str (:error @turn-result))}
        {:stopReason "end_turn"}))))

(defn- emit-status-notification! [output-writer data]
  (rpc/write-message! output-writer
                      {:jsonrpc "2.0"
                       :method  "chat/status"
                       :params  data}))

(defn- run-prompt [state-dir output-writer session-id text ctx]
  (let [{:keys [soul model provider provider-config context-window]} ctx]
    (if (bridge/slash-command? text)
      (let [result (bridge/dispatch state-dir session-id text ctx nil)]
        (case (:command result)
          :status
          (do
            (emit-status-notification! output-writer (:data result))
            {:stopReason "end_turn"})
          (do
            (rpc/write-message! output-writer
                                {:jsonrpc "2.0"
                                 :method  "chat/error"
                                 :params  {:message (:message result)}})
            {:stopReason "end_turn"})))
      (run-turn state-dir output-writer session-id text soul model provider provider-config context-window))))

(defn- session-prompt-handler [state-dir output-writer agents models provider-configs cfg home model-override params _message]
  (let [session-id (get params :sessionId)
        text       (prompt->text (get params :prompt))
        agent-id   (or (:crew (storage/get-session state-dir session-id)) (:agent (storage/get-session state-dir session-id)) "main")]
    (when (nil? session-id)
      (throw (ex-info "sessionId is required" {:code -32602})))
    (when (nil? text)
      (throw (ex-info "Invalid params: no text in prompt" {:code -32602})))
    (let [{:keys [soul model provider provider-config context-window] :as ctx}
          (assoc (resolve-agent-model agents models provider-configs cfg home model-override agent-id)
                  :crew agent-id :agent agent-id)]
        (if (nil? model)
          (do
            (binding [*out* *err*]
              (println (str "no model configured for crew: " agent-id)))
          {:stopReason "error" :error (str "no model configured for crew: " agent-id)})
        (let [session-entry (storage/get-session state-dir session-id)
              ctx          (cond-> ctx
                             (:model session-entry) (assoc :model (:model session-entry))
                             (:provider session-entry) (assoc :provider (:provider session-entry)))]
          (run-prompt state-dir output-writer session-id text ctx))))))

(defn handlers
  [{:keys [state-dir agent-id agents models provider-configs cfg home output-writer model-override] :or {agent-id "main"}}]
  (let [opts {:agents agents :models models :provider-configs provider-configs :cfg cfg :home home :agent-id agent-id :model-override model-override}]
    {"initialize"      (partial initialize-handler opts)
     "session/new"     (partial session-new-handler state-dir agent-id)
     "session/load"    (partial session-load-handler state-dir agent-id)
     "session/prompt"  (partial session-prompt-handler state-dir output-writer (or agents {}) (or models {}) (or provider-configs {}) cfg home model-override)
     "session/cancel"  session-cancel-handler}))

(defn dispatch-line
  [opts line]
  (rpc/handle-line (handlers opts) line))
