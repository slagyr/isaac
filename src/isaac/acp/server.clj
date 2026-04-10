(ns isaac.acp.server
  (:require
    [isaac.acp.rpc :as rpc]
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

(defn handlers
  [{:keys [state-dir agent-id] :or {agent-id "main"}}]
  {"initialize"  initialize-handler
   "session/new" (partial session-new-handler state-dir agent-id)})

(defn dispatch-line
  [opts line]
  (rpc/handle-line (handlers opts) line))
