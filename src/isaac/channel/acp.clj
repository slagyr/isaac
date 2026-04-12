(ns isaac.channel.acp
  (:require
    [clojure.string :as str]
    [isaac.acp.rpc :as rpc]
    [isaac.channel :as channel]))

(defn- write! [output-writer message]
  (rpc/write-message! output-writer message))

(defn- text-notification [session-id text]
  {:jsonrpc "2.0"
   :method  "session/update"
   :params  {:sessionId session-id
             :update    {:sessionUpdate "agent_message_chunk"
                         :content       {:type "text"
                                         :text text}}}})

(defn- tool-call-notification [session-id tool-call]
  {:jsonrpc "2.0"
   :method  "session/update"
   :params  {:sessionId session-id
             :update {:sessionUpdate "tool_call"
                       :status        "pending"
                       :toolCallId    (:id tool-call)
                       :toolName      (:name tool-call)
                       :input         (:arguments tool-call)}}})

(defn- tool-result-notification [session-id result]
  {:jsonrpc "2.0"
   :method  "session/update"
   :params  {:sessionId session-id
             :update {:sessionUpdate "tool_call_update"
                       :status        "completed"
                       :output        result}}})

(deftype AcpChannel [output-writer]
  channel/Channel
  (on-turn-start [_ _ _] nil)
  (on-text-chunk [_ session-key text]
    (let [display (some-> text str/trim)]
      (when (seq display)
        (write! output-writer (text-notification session-key display)))))
  (on-tool-call [_ session-key tool-call]
    (write! output-writer (tool-call-notification session-key tool-call)))
  (on-tool-result [_ session-key _ result]
    (write! output-writer (tool-result-notification session-key result)))
  (on-turn-end [_ _ _] nil)
  (on-error [_ _ _] nil))

(defn channel [output-writer]
  (->AcpChannel output-writer))
