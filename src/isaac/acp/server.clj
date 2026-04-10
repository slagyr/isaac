(ns isaac.acp.server
  (:require
    [clojure.string :as str]
    [isaac.acp.rpc :as rpc]
    [isaac.cli.chat :as chat]
    [isaac.session.key :as key]
    [isaac.session.storage :as storage]
    [isaac.tool.registry :as tool-registry]))

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
  (let [agent-cfg   (get agents agent-id)
        model-alias (:model agent-cfg)
        model-cfg   (get models model-alias)]
    {:soul            (:soul agent-cfg)
     :model           (:model model-cfg)
     :provider        (:provider model-cfg)
     :context-window  (:contextWindow model-cfg)
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
      (nil? content)    nil
      :else             (str content))))

(defn- chunk-piece [full-content chunk]
  (when-let [content (chunk-content chunk)]
    (if (and (:done chunk)
             (seq full-content)
             (str/starts-with? content full-content))
      (subs content (count full-content))
      content)))

(defn- text-notification [session-id text]
  {:jsonrpc "2.0"
   :method  "session/update"
   :params  {:sessionId session-id
             :update    {:sessionUpdate "agent_message_chunk"
                         :text          text}}})

(defn- tool-call-notifications [tc]
  (let [name      (get-in tc [:function :name])
        args      (get-in tc [:function :arguments])
        tc-id     (str (random-uuid))
        result    ((tool-registry/tool-fn) name args)]
    {:notifications [{:jsonrpc "2.0"
                      :method  "session/update"
                      :params  {:update {:sessionUpdate "tool_call"
                                         :status        "pending"
                                         :toolCallId    tc-id
                                         :toolName      name
                                         :input         args}}}
                     {:jsonrpc "2.0"
                      :method  "session/update"
                      :params  {:update {:sessionUpdate "tool_call_update"
                                         :status        "completed"
                                         :output        result}}}]
     :tool-message {:role "tool" :content (str result)}}))

(defn- stream-once! [session-id provider provider-config req]
  (let [full-text  (atom "")
        updates    (atom [])
        final      (atom nil)
        raw-result (chat/dispatch-chat-stream provider provider-config req
                     (fn [chunk]
                       (when-let [piece (chunk-piece @full-text chunk)]
                         (when (seq piece)
                           (swap! full-text str piece)
                           (when-let [display (not-empty (str/trim piece))]
                             (swap! updates conj (text-notification session-id display)))))
                       (when (:done chunk)
                         (reset! final chunk))))]
    {:raw-result     raw-result
     :full-text      @full-text
     :notifications  @updates
     :final-chunk    @final}))

(defn- session-prompt-handler [state-dir agents models provider-configs params _message]
  (let [session-id (get params :sessionId)
        text       (prompt->text (get params :prompt))
        agent-id   (:agent (storage/parse-key session-id))]
    (when (nil? text)
      (throw (ex-info "Invalid params: no text in prompt" {:code -32602})))
    (let [{:keys [soul model provider provider-config]}
          (resolve-agent-model agents models provider-configs agent-id)]
      (storage/append-message! state-dir session-id {:role "user" :content text})
      (let [transcript       (storage/get-transcript state-dir session-id)
            initial-request  (chat/build-chat-request provider provider-config
                                                      {:model model :soul soul :transcript transcript})]
        (loop [req              initial-request
               all-notifications []
               loops            0]
          (let [{:keys [raw-result full-text notifications final-chunk]} (stream-once! session-id provider provider-config req)]
            (if (:error raw-result)
              {:result        {:stopReason "error" :error (str (:error raw-result))}
               :notifications (into all-notifications notifications)}
              (let [tool-calls (get-in final-chunk [:message :tool_calls])]
                (if (and (seq tool-calls) (< loops 10))
                  (let [tc-results       (mapv tool-call-notifications tool-calls)
                        tc-notifications (mapcat :notifications tc-results)
                        tool-msgs        (mapv :tool-message tc-results)
                        assistant-msg    {:role "assistant" :content (or (not-empty full-text) "") :tool_calls tool-calls}
                        new-messages     (into (:messages req) (cons assistant-msg tool-msgs))]
                    (recur (assoc req :messages new-messages)
                           (into all-notifications (concat notifications tc-notifications))
                           (inc loops)))
                  (let [result {:content  (or (not-empty full-text)
                                              (get-in raw-result [:message :content])
                                              "")
                                :response (or final-chunk raw-result)}]
                    (chat/process-response! state-dir session-id result {:model model :provider provider})
                    {:result        {:stopReason "end_turn"}
                     :notifications (into all-notifications notifications)}))))))))))

(defn handlers
  [{:keys [state-dir agent-id agents models provider-configs] :or {agent-id "main"}}]
  {"initialize"     initialize-handler
   "session/new"    (partial session-new-handler state-dir agent-id)
   "session/prompt" (partial session-prompt-handler state-dir (or agents {}) (or models {}) (or provider-configs {}))})

(defn dispatch-line
  [opts line]
  (rpc/handle-line (handlers opts) line))
