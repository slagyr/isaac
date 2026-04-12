(ns isaac.acp.server
  (:require
    [isaac.acp.rpc :as rpc]
    [isaac.channel.acp :as acp-channel]
    [isaac.cli.chat.single-turn :as single-turn]
    [isaac.config.resolution :as config]
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

(defn- initialize-result []
  {:protocolVersion   1
   :agentInfo         {:name "isaac" :version "dev"}
   :agentCapabilities {:loadSession true
                       :promptCapabilities {:text true}}})

(defn- initialize-handler [_params _message]
  (initialize-result))

(defn- with-startup-cwd [f]
  (let [original (System/getProperty "user.dir")]
    (try
      (when-not (= startup-cwd original)
        (System/setProperty "user.dir" startup-cwd))
      (f)
      (finally
        (when-not (= startup-cwd original)
          (System/setProperty "user.dir" original))))))

(defn- new-acp-session-key [agent-id]
  (key/build-key {:agent agent-id
                  :channel "acp"
                  :chatType "direct"
                  :conversation (str (random-uuid))}))

(defn- session-new-handler [state-dir agent-id _params _message]
  (let [session-key (new-acp-session-key agent-id)]
    (with-startup-cwd #(storage/create-session! state-dir session-key))
    {:sessionId session-key}))

(defn- resolve-agent-model [agents models provider-configs cfg home agent-id]
  (if cfg
    (config/resolve-agent-context cfg agent-id {:home home})
    (let [agent-cfg   (get agents agent-id)
          model-alias (:model agent-cfg)
          model-cfg   (get models model-alias)]
      {:soul            (:soul agent-cfg)
       :model           (:model model-cfg)
       :provider        (:provider model-cfg)
       :context-window  (:contextWindow model-cfg)
       :provider-config (or (get provider-configs (:provider model-cfg)) {})})))

(defn- prompt->text [prompt]
  (->> (or prompt [])
       (filter #(= "text" (:type %)))
       first
       :text))

(defn- session-cancel-handler [params _message]
  (let [session-id (get params :sessionId)]
    (interrupt! session-id)
    nil))

(defn- run-prompt [state-dir output-writer session-id text soul model provider provider-config context-window]
  (if (interrupted? session-id)
    (do
      (clear-interrupt! session-id)
      {:stopReason "cancelled"})
    (let [channel       (acp-channel/channel output-writer)
          turn-result   (atom nil)]
      (with-out-str
        (reset! turn-result
                (with-startup-cwd
                  #(single-turn/process-user-input! state-dir session-id text
                                             {:model           model
                                              :soul            soul
                                              :provider        provider
                                              :provider-config provider-config
                                               :context-window  context-window
                                               :channel         channel}))))
      (if (:error @turn-result)
        {:stopReason "error" :error (str (:error @turn-result))}
        {:stopReason "end_turn"}))))

(defn- session-prompt-handler [state-dir output-writer agents models provider-configs cfg home params _message]
  (let [session-id (get params :sessionId)
        text       (prompt->text (get params :prompt))
        agent-id   (:agent (storage/parse-key session-id))]
    (when (nil? text)
      (throw (ex-info "Invalid params: no text in prompt" {:code -32602})))
    (let [{:keys [soul model provider provider-config context-window]}
          (resolve-agent-model agents models provider-configs cfg home agent-id)]
      (if (nil? model)
        (do
          (binding [*out* *err*]
            (println (str "no model configured for agent: " agent-id)))
          {:stopReason "error" :error (str "no model configured for agent: " agent-id)})
        (run-prompt state-dir output-writer session-id text soul model provider provider-config context-window)))))

(defn handlers
  [{:keys [state-dir agent-id agents models provider-configs cfg home output-writer] :or {agent-id "main"}}]
  {"initialize"      initialize-handler
   "session/new"     (partial session-new-handler state-dir agent-id)
   "session/prompt"  (partial session-prompt-handler state-dir output-writer (or agents {}) (or models {}) (or provider-configs {}) cfg home)
   "session/cancel"  session-cancel-handler})

(defn dispatch-line
  [opts line]
  (rpc/handle-line (handlers opts) line))
