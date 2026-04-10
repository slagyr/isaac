(ns isaac.channel.acp
  (:require
    [clojure.string :as str]
    [isaac.channel :as channel]))

(defn- append! [notifications notification]
  (swap! notifications conj notification))

(defn- text-notification [session-id text]
  {:jsonrpc "2.0"
   :method  "session/update"
   :params  {:sessionId session-id
             :update    {:sessionUpdate "agent_message_chunk"
                         :content       {:type "text"
                                         :text text}}}})

(defn- tool-call-notification [tool-call]
  {:jsonrpc "2.0"
   :method  "session/update"
   :params  {:update {:sessionUpdate "tool_call"
                      :status        "pending"
                      :toolCallId    (:id tool-call)
                      :toolName      (:name tool-call)
                      :input         (:arguments tool-call)}}})

(defn- tool-result-notification [result]
  {:jsonrpc "2.0"
   :method  "session/update"
   :params  {:update {:sessionUpdate "tool_call_update"
                      :status        "completed"
                      :output        result}}})

(deftype AcpChannel [notifications]
  channel/Channel
  (on-turn-start [_ _ _] nil)
  (on-text-chunk [_ session-key text]
    (let [display (some-> text str/trim)]
      (when (seq display)
        (append! notifications (text-notification session-key display)))))
  (on-tool-call [_ _ tool-call]
    (append! notifications (tool-call-notification tool-call)))
  (on-tool-result [_ _ _ result]
    (append! notifications (tool-result-notification result)))
  (on-turn-end [_ _ _] nil)
  (on-error [_ _ _] nil))

(defn channel [notifications]
  (->AcpChannel notifications))
