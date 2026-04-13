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

(defn- tool-kind [tool-name]
  (case tool-name
    "read"  "read"
    "edit"  "edit"
    "write" "edit"
    "exec"  "execute"
    "other"))

(defn- tool-title [tool-name arguments]
  (let [summary (or (:command arguments)
                    (:filePath arguments)
                    (:file_path arguments)
                    (first (vals arguments)))]
    (if summary
      (str tool-name ": " summary)
      tool-name)))

(defn- tool-call-notification [session-id tool-call]
  {:jsonrpc "2.0"
   :method  "session/update"
   :params  {:sessionId session-id
             :update {:sessionUpdate "tool_call"
                       :status        "pending"
                       :toolCallId    (:id tool-call)
                       :title         (tool-title (:name tool-call) (:arguments tool-call))
                       :kind          (tool-kind (:name tool-call))
                       :rawInput      (:arguments tool-call)}}})

(defn- tool-result-notification [session-id tool-call result]
  {:jsonrpc "2.0"
   :method  "session/update"
   :params  {:sessionId session-id
             :update {:sessionUpdate "tool_call_update"
                       :toolCallId    (:id tool-call)
                       :status        "completed"
                       :rawOutput     result
                       :content       [{:type    "content"
                                        :content {:type "text"
                                                  :text (str result)}}]}}})

(deftype AcpChannel [output-writer]
  channel/Channel
  (on-turn-start [_ _ _] nil)
  (on-text-chunk [_ session-key text]
    (let [display (some-> text str/trim)]
      (when (seq display)
        (write! output-writer (text-notification session-key display)))))
  (on-tool-call [_ session-key tool-call]
    (write! output-writer (tool-call-notification session-key tool-call)))
  (on-tool-result [_ session-key tool-call result]
    (write! output-writer (tool-result-notification session-key tool-call result)))
  (on-turn-end [_ _ _] nil)
  (on-error [_ _ _] nil))

(defn channel [output-writer]
  (->AcpChannel output-writer))
