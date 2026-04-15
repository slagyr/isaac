(ns isaac.acp.server
  (:require
    [isaac.acp.jsonrpc :as jrpc]
    [isaac.acp.rpc :as rpc]
    [isaac.channel.acp :as acp-channel]
    [isaac.cli.chat.single-turn :as single-turn]
    [isaac.config.resolution :as config]
    [isaac.session.bridge :as bridge]
    [isaac.session.key :as key]
    [isaac.session.storage :as storage]))

(def ^:private startup-cwd (System/getProperty "user.dir"))

(defn- with-startup-cwd [f]
  (let [original (System/getProperty "user.dir")]
    (try
      (when-not (= startup-cwd original)
        (System/setProperty "user.dir" startup-cwd))
      (f)
      (finally
        (when-not (= startup-cwd original)
          (System/setProperty "user.dir" original))))))

(defn- invalid-params [message]
  (ex-info message {:type :invalid-params
                    :message message}))

(defn- session-new-handler [state-dir agent-id params message]
  (try
    (let [session (with-startup-cwd #(storage/create-session! state-dir (:name params) {:crew agent-id :agent agent-id :channel "acp" :chatType "direct"}))]
      {:notifications [(acp-channel/available-commands-update (:id session) (bridge/available-commands))]
       :result        {:sessionId (:id session)}})
    (catch clojure.lang.ExceptionInfo e
      (if (= -32602 (:code (ex-data e)))
        (let [session (storage/get-session state-dir (:name params))]
          {:notifications (cond-> [] session (conj (acp-channel/available-commands-update (:id session) (bridge/available-commands))))
           :response      {:jsonrpc "2.0"
                           :id      (:id message)
                           :error   {:code    jrpc/INVALID_PARAMS
                                     :message (ex-message e)}}})
        (throw e)))))

(defn- session-load-handler [state-dir _agent-id params _message]
  (if-let [session (storage/open-session state-dir (:sessionId params))]
    {:sessionId (:id session)}
    (throw (invalid-params (str "session not found: " (:sessionId params))))))

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
    (bridge/cancel! session-id)
    nil))

(defn- emit-status-notification! [output-writer data]
  (rpc/write-message! output-writer
                      (jrpc/notification "chat/status" data)))

(defn- emit-command-text! [output-writer session-id text]
  (rpc/write-message! output-writer (acp-channel/text-update session-id text)))

(defn- end-turn-with-error! [output-writer session-id message]
  (emit-command-text! output-writer session-id message)
  {:stopReason "end_turn"})

(defn- run-turn [state-dir output-writer session-id text soul model provider provider-config context-window crew-members]
  (let [channel     (acp-channel/channel output-writer)
        turn-result (atom nil)]
    (with-out-str
      (reset! turn-result
              (with-startup-cwd
                #(single-turn/process-user-input! state-dir session-id text
                                                  {:model           model
                                                   :crew-members    crew-members
                                                   :soul            soul
                                                   :provider        provider
                                                   :provider-config provider-config
                                                   :context-window  context-window
                                                   :channel         channel}))))
    (cond
      (bridge/cancelled-response? @turn-result)
      @turn-result

      (:error @turn-result)
      (end-turn-with-error! output-writer session-id (single-turn/error-message @turn-result))

      :else
      {:stopReason "end_turn"})))

(defn- run-prompt [state-dir output-writer session-id text ctx]
  (let [{:keys [crew-members soul model provider provider-config context-window]} ctx]
    (if (bridge/slash-command? text)
      (let [result (bridge/dispatch state-dir session-id text ctx nil)]
        (case (:command result)
          :status
          (do
            (emit-command-text! output-writer session-id (bridge/format-status (:data result)))
            (emit-status-notification! output-writer (:data result))
            {:stopReason "end_turn"})
          (do
            (emit-command-text! output-writer session-id (:message result))
            {:stopReason "end_turn"})))
      (run-turn state-dir output-writer session-id text soul model provider provider-config context-window crew-members))))

(defn- session-prompt-handler [state-dir output-writer agents models provider-configs cfg home model-override params _message]
  (let [session-id     (get params :sessionId)
        text           (prompt->text (get params :prompt))
        session-entry  (when session-id (storage/get-session state-dir session-id))
        agent-id       (or (:crew session-entry) (:agent session-entry) "main")]
    (when (nil? session-id)
      (throw (invalid-params "sessionId is required")))
    (when (nil? text)
      (throw (invalid-params "Invalid params: no text in prompt")))
    (let [{:keys [soul model provider provider-config context-window] :as ctx}
          (assoc (resolve-agent-model agents models provider-configs cfg home model-override agent-id)
                  :crew agent-id :agent agent-id)]
      (if (nil? model)
        (let [message (str "no model configured for crew: " agent-id)]
          (binding [*out* *err*]
            (println message))
          (end-turn-with-error! output-writer session-id message))
        (let [ctx (cond-> ctx
                    true (assoc :crew-members agents)
                    true (assoc :models models)
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
