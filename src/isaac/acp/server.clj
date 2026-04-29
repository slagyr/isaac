(ns isaac.acp.server
  (:require
    [isaac.acp.jsonrpc :as jrpc]
    [isaac.acp.rpc :as rpc]
    [isaac.comm.acp :as acp-comm]
    [isaac.drive.turn :as single-turn]
    [isaac.config.loader :as config]
    [isaac.logger :as log]
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

(defn- session-new-handler [state-dir crew-id params message]
  (try
    (let [session (with-startup-cwd #(storage/create-session! state-dir (:name params) {:crew     crew-id
                                                                                        :channel  "acp"
                                                                                        :chatType "direct"
                                                                                        :origin   {:kind :acp}}))]
      {:notifications [(acp-comm/available-commands-update (:id session) (bridge/available-commands))]
       :result        {:sessionId (:id session)}})
    (catch clojure.lang.ExceptionInfo e
      (if (= -32602 (:code (ex-data e)))
        (let [session (storage/get-session state-dir (:name params))]
          {:notifications (cond-> [] session (conj (acp-comm/available-commands-update (:id session) (bridge/available-commands))))
           :response      {:jsonrpc "2.0"
                           :id      (:id message)
                           :error   {:code    jrpc/INVALID_PARAMS
                                     :message (ex-message e)}}})
        (throw e)))))

(defn- session-load-handler [state-dir _crew-id params _message]
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

(defn- resolve-crew-model [crew-members models provider-configs cfg home model-override crew-id]
  (let [lookup-model (fn [model-key]
                        (or (get models model-key)
                            (get models (keyword model-key))))]
    (if cfg
      (let [cfg (config/normalize-config cfg)]
        (if model-override
          (let [ctx          (config/resolve-crew-context cfg crew-id {:home home})
                alias-match  (or (get-in cfg [:models model-override])
                                 (get-in cfg [:models (keyword model-override)]))
                parsed       (when-not alias-match (config/parse-model-ref model-override))
                provider     (or (:provider alias-match) (:provider parsed))
                provider-cfg (when provider (config/resolve-provider cfg provider))]
            (if (or alias-match parsed)
              (assoc ctx
                :model           (or (:model alias-match) (:model parsed))
                :provider        provider
                :context-window  (or (:context-window alias-match)
                                     (:context-window provider-cfg)
                                     (:context-window ctx)
                                     32768)
                :provider-config (or provider-cfg {}))
              ctx))
          (config/resolve-crew-context cfg crew-id {:home home})))
      (let [crew-cfg    (get crew-members crew-id)
            model-alias (or model-override (:model crew-cfg))
            model-cfg   (lookup-model model-alias)]
        {:soul            (:soul crew-cfg)
         :model           (:model model-cfg)
         :provider        (:provider model-cfg)
         :context-window  (:context-window model-cfg)
         :provider-config (or (get provider-configs (:provider model-cfg)) {})}))))

(defn- resolve-crew-members [crew-members cfg]
  (or crew-members
      (some-> cfg config/normalize-config :crew)
      {}))

(defn- initialize-handler [opts _params _message]
  (let [{:keys [crew-id crew-members models provider-configs cfg home model-override] :or {crew-id "main"}} opts
        {:keys [model provider]} (resolve-crew-model (or crew-members {}) (or models {}) (or provider-configs {}) cfg home model-override crew-id)]
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
  (rpc/write-message! output-writer (acp-comm/text-update session-id text)))

(defn- end-turn-with-error! [output-writer session-id message]
  (emit-command-text! output-writer session-id message)
  {:stopReason "end_turn"})

(defn- run-acp-turn [state-dir output-writer session-id text soul model provider provider-config context-window crew-members]
  (try
    (let [channel     (acp-comm/channel output-writer)
          turn-result (atom nil)]
      (with-out-str
        (reset! turn-result
                (with-startup-cwd
                  #(single-turn/run-turn! state-dir session-id text
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
        (if (:already-emitted? @turn-result)
          {:stopReason "end_turn"}
          (end-turn-with-error! output-writer session-id (single-turn/error-message @turn-result)))

        :else
        {:stopReason "end_turn"}))
    (catch Exception e
      (log/ex :acp/turn-error e :session session-id)
      (end-turn-with-error! output-writer session-id (or (.getMessage e) "Unexpected error")))))

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
      (run-acp-turn state-dir output-writer session-id text soul model provider provider-config context-window crew-members))))

(defn- session-prompt-handler [state-dir output-writer crew-members models provider-configs cfg home model-override params _message]
  (let [session-id     (get params :sessionId)
         text           (prompt->text (get params :prompt))
         session-entry  (when session-id (storage/get-session state-dir session-id))
         crew-id        (or (:crew session-entry) "main")
         crew-members   (resolve-crew-members crew-members cfg)
         unknown-crew?  (and (or (:crew session-entry) (:agent session-entry))
                             (not (contains? crew-members crew-id)))]
    (when (nil? session-id)
      (throw (invalid-params "sessionId is required")))
    (when (nil? text)
      (throw (invalid-params "Invalid params: no text in prompt")))
    (let [{:keys [soul model provider provider-config context-window] :as ctx}
          (assoc (resolve-crew-model crew-members (or models {}) (or provider-configs {}) cfg home model-override crew-id)
                  :crew crew-id)]
      (if (and (nil? model) (not unknown-crew?))
        (let [message (str "no model configured for crew: " crew-id)]
          (binding [*out* *err*]
            (println message))
          (end-turn-with-error! output-writer session-id message))
        (let [ctx (cond-> ctx
                    true (assoc :crew-members crew-members)
                    true (assoc :models (or models {}))
                    (:model session-entry) (assoc :model (:model session-entry))
                    (:provider session-entry) (assoc :provider (:provider session-entry)))]
          (run-prompt state-dir output-writer session-id text ctx))))))

(defn handlers
  [{:keys [state-dir crew-id crew-members models provider-configs cfg home output-writer model-override] :or {crew-id "main"}}]
  (let [opts {:crew-members crew-members :models models :provider-configs provider-configs :cfg cfg :home home :crew-id crew-id :model-override model-override}]
    {"initialize"      (partial initialize-handler opts)
     "session/new"     (partial session-new-handler state-dir crew-id)
     "session/load"    (partial session-load-handler state-dir crew-id)
     "session/prompt"  (partial session-prompt-handler state-dir output-writer crew-members models provider-configs cfg home model-override)
     "session/cancel"  session-cancel-handler}))

(defn dispatch-line
  [opts line]
  (rpc/handle-line (handlers opts) line))
