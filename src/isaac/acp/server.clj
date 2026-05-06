(ns isaac.acp.server
  (:require
    [cheshire.core :as json]
    [isaac.acp.jsonrpc :as jrpc]
    [isaac.acp.rpc :as rpc]
    [isaac.comm.acp :as acp-comm]
    [isaac.drive.turn :as single-turn]
    [isaac.config.loader :as config]
    [isaac.logger :as log]
    [isaac.bridge :as bridge]
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
                provider-id  (or (:provider alias-match) (:provider parsed))
                provider-cfg (when provider-id (config/resolve-provider cfg provider-id))]
            (if (or alias-match parsed)
              (assoc ctx
                :model          (or (:model alias-match) (:model parsed))
                :provider       (when provider-id
                                  ((requiring-resolve 'isaac.drive.dispatch/make-provider)
                                   provider-id (or provider-cfg {})))
                :context-window (or (:context-window alias-match)
                                    (:context-window provider-cfg)
                                    (:context-window ctx)
                                    32768))
              ctx))
          (config/resolve-crew-context cfg crew-id {:home home})))
      (let [crew-cfg     (get crew-members crew-id)
            model-alias  (or model-override (:model crew-cfg))
            model-cfg    (lookup-model model-alias)
            provider-id  (:provider model-cfg)
            provider-cfg (or (get provider-configs provider-id) {})]
        {:soul           (:soul crew-cfg)
         :model          (:model model-cfg)
         :provider       (when provider-id
                           ((requiring-resolve 'isaac.drive.dispatch/make-provider)
                            provider-id provider-cfg))
         :context-window (:context-window model-cfg)}))))

(defn- resolve-crew-members [crew-members cfg]
  (or crew-members
      (some-> cfg config/normalize-config :crew)
      {}))

(defn- initialize-handler [opts _params _message]
  (let [{:keys [crew-id crew-members models provider-configs cfg home model-override] :or {crew-id "main"}} opts
        {:keys [model provider]} (resolve-crew-model (or crew-members {}) (or models {}) (or provider-configs {}) cfg home model-override crew-id)]
    (initialize-result model
                       (when provider
                         ((requiring-resolve 'isaac.provider/display-name) provider)))))

(defn- prompt->text [prompt]
  (->> (or prompt [])
       (filter #(= "text" (:type %)))
       first
       :text))

(defn- content->text [content]
  (cond
    (string? content)
    content

    (and (vector? content) (every? map? content))
    (->> content
         (filter #(= "text" (:type %)))
         (map :text)
         (apply str))

    :else
    nil))

(defn- extract-tool-calls [message]
  (cond
    (= "toolCall" (:type message))
    [{:type "toolCall" :id (:id message) :name (:name message) :arguments (:arguments message)}]

    (and (vector? (:content message))
         (= "toolCall" (:type (first (:content message)))))
    (:content message)

    (and (string? (:content message))
         (.startsWith ^String (:content message) "["))
    (try
      (let [parsed (json/parse-string (:content message) true)]
        (when (and (sequential? parsed) (= "toolCall" (:type (first parsed))))
          (vec parsed)))
      (catch Exception _ nil))

    :else
    nil))

(defn- tool-results-by-id [transcript]
  (->> transcript
       (keep (fn [entry]
               (let [message (:message entry)
                     role    (:role message)
                     tc-id   (or (:toolCallId message) (:id message))]
                 (when (and (= "message" (:type entry))
                            (= "toolResult" role)
                            tc-id)
                   [tc-id (or (content->text (:content message))
                              (some-> (:content message) str))]))))
       (into {})))

(defn- replay-transcript-entry! [output-writer session-id tool-results entry]
  (case (:type entry)
    "compaction"
    (when-let [summary (:summary entry)]
      (rpc/write-message! output-writer (acp-comm/text-update session-id summary)))

    "message"
    (let [message    (:message entry)
          role       (:role message)
          tool-calls (extract-tool-calls message)]
      (cond
        (seq tool-calls)
        (doseq [tool-call tool-calls]
          (rpc/write-message! output-writer
                              (acp-comm/replay-tool-call-update session-id tool-call (get tool-results (:id tool-call)))))

        (= "user" role)
        (when-let [text (content->text (:content message))]
          (rpc/write-message! output-writer (acp-comm/user-text-update session-id text)))

        (= "assistant" role)
        (when-let [text (content->text (:content message))]
          (rpc/write-message! output-writer (acp-comm/text-update session-id text)))))

    nil))

(defn- replay-transcript! [output-writer session-id transcript]
  (when output-writer
    (let [tool-results (tool-results-by-id transcript)]
      (doseq [entry transcript]
        (replay-transcript-entry! output-writer session-id tool-results entry)))))

(defn attach-session-result! [state-dir output-writer session-key]
  (if-let [session (storage/open-session state-dir session-key)]
    (do
      (replay-transcript! output-writer (:id session) (storage/get-transcript state-dir (:id session)))
      {:sessionId (:id session)})
    (throw (invalid-params (str "session not found: " session-key)))))

(defn- session-load-handler [state-dir output-writer _crew-id params _message]
  (if-let [session-id (:sessionId params)]
    (do
      (attach-session-result! state-dir output-writer session-id)
      nil)
    (throw (invalid-params "sessionId is required"))))

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

(defn- run-prompt [state-dir output-writer session-id text ctx]
  (let [channel (acp-comm/channel output-writer)
        opts    (assoc ctx :channel channel)
        result  (try
                  (with-startup-cwd #(bridge/dispatch! state-dir session-id text opts))
                  (catch Exception e
                    (log/ex :acp/turn-error e :session session-id)
                    {:error :exception :message (or (.getMessage e) "Unexpected error")}))]
    (cond
      (bridge/cancelled-response? result)
      result

      (:error result)
      (if (:already-emitted? result)
        {:stopReason "end_turn"}
        (end-turn-with-error! output-writer session-id (single-turn/error-message result)))

      (= :status (:command result))
      (do
        (emit-status-notification! output-writer (:data result))
        {:stopReason "end_turn"})

      :else
      {:stopReason "end_turn"})))

(defn- session-prompt-handler [state-dir output-writer crew-members models provider-configs cfg home model-override params _message]
  (let [session-id     (get params :sessionId)
        text            (prompt->text (get params :prompt))
        session-entry   (when session-id (storage/get-session state-dir session-id))
        crew-id         (or (:crew session-entry) "main")
        default-crew-id (some-> cfg config/normalize-config :defaults :crew)
        crew-members    (resolve-crew-members crew-members cfg)
        unknown-crew?   (and (or (:crew session-entry) (:agent session-entry))
                             (not (or (= crew-id "main")
                                      (contains? crew-members crew-id)
                                      (= crew-id default-crew-id))))]
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
     "session/load"    (partial session-load-handler state-dir output-writer crew-id)
     "session/prompt"  (partial session-prompt-handler state-dir output-writer crew-members models provider-configs cfg home model-override)
     "session/cancel"  session-cancel-handler}))

(defn dispatch-line
  [opts line]
  (rpc/handle-line (handlers opts) line))
