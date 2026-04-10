(ns isaac.acp.server
  (:require
    [clojure.string :as str]
    [isaac.acp.rpc :as rpc]
    [isaac.cli.chat :as chat]
    [isaac.session.key :as key]
    [isaac.session.storage :as storage]))

(def ^:private startup-cwd (System/getProperty "user.dir"))

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

(defn- resolve-agent-model [agents models provider-configs agent-id]
  (let [agent-cfg  (get agents agent-id)
        model-alias (:model agent-cfg)
        model-cfg  (get models model-alias)]
    {:soul           (:soul agent-cfg)
     :model          (:model model-cfg)
     :provider       (:provider model-cfg)
     :context-window (:contextWindow model-cfg)
     :provider-config (or (get provider-configs (:provider model-cfg)) {})}))

(defn- prompt->text [prompt]
  (->> (or prompt [])
       (filter #(= "text" (:type %)))
       first
       :text))

(defn- chunk-content [chunk]
  (let [content (or (get-in chunk [:message :content])
                    (get-in chunk [:delta :text])
                    (get-in chunk [:choices 0 :delta :content]))]
    (cond
      (string? content) content
      (vector? content) (apply str content)
      (nil? content) nil
      :else (str content))))

(defn- chunk-piece [full-content chunk]
  (when-let [content (chunk-content chunk)]
    (if (and (:done chunk)
             (seq full-content)
             (str/starts-with? content full-content))
      (subs content (count full-content))
      content)))

(defn- notification [session-id text]
  {:jsonrpc "2.0"
   :method  "session/update"
   :params  {:sessionId session-id
             :update    {:sessionUpdate "agent_message_chunk"
                         :text          text}}})

(defn- session-prompt-handler [state-dir agents models provider-configs params _message]
  (let [session-id (get params :sessionId)
        text       (prompt->text (get params :prompt))
        agent-id   (:agent (storage/parse-key session-id))]
    (when (nil? text)
      (throw (ex-info "Invalid params: no text in prompt" {:code -32602})))
    (let [{:keys [soul model provider provider-config context-window]}
          (resolve-agent-model agents models provider-configs agent-id)]
      (storage/append-message! state-dir session-id {:role "user" :content text})
      (let [transcript (storage/get-transcript state-dir session-id)
            request    (chat/build-chat-request provider provider-config
                                                {:model model :soul soul :transcript transcript})
            full-text  (atom "")
            updates    (atom [])
            final      (atom nil)
            raw-result (chat/dispatch-chat-stream provider provider-config request
                                                  (fn [chunk]
                                                    (when-let [piece (chunk-piece @full-text chunk)]
                                                      (when (seq piece)
                                                        (swap! full-text str piece)
                                                        (when-let [display (not-empty (str/trim piece))]
                                                          (swap! updates conj (notification session-id display)))))
                                                    (when (:done chunk)
                                                      (reset! final chunk))))]
        (if (:error raw-result)
          {:result {:stopReason "error" :error (str (:error raw-result))}
           :notifications @updates}
          (let [result {:content  (or (not-empty @full-text)
                                      (get-in raw-result [:message :content])
                                      "")
                        :response (or @final raw-result)}]
            (chat/process-response! state-dir session-id result {:model model :provider provider})
            {:result {:stopReason "end_turn"}
             :notifications @updates}))))))

(defn handlers
  [{:keys [state-dir agent-id agents models provider-configs] :or {agent-id "main"}}]
  {"initialize"    initialize-handler
   "session/new"   (partial session-new-handler state-dir agent-id)
   "session/prompt" (partial session-prompt-handler state-dir (or agents {}) (or models {}) (or provider-configs {}))})

(defn dispatch-line
  [opts line]
  (rpc/handle-line (handlers opts) line))
