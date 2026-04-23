(ns isaac.cli.acp-proxy-reconnect-spec
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [isaac.acp.jsonrpc :as jrpc]
    [isaac.acp.ws :as ws]
    [isaac.cli.acp :as sut]
    [isaac.fs :as fs]
    [isaac.session.storage :as storage]
    [speclj.core :refer :all])
  (:import
    (java.io BufferedReader StringReader StringWriter)
    (java.util.concurrent LinkedBlockingQueue)))

(def base-opts
  {:state-dir "/test/acp-proxy"
   :agents    {"main" {:name "main" :soul "You are Isaac." :model "grover"}}
   :models    {"grover" {:alias "grover" :model "echo" :provider "grover" :context-window 32768}}})

(defn- output-messages [output]
  (->> (str/split-lines output)
       (remove str/blank?)
       (mapv #(json/parse-string % true))))

(defn- queued-read-line [queue]
  (fn []
    (let [value (.take ^LinkedBlockingQueue queue)]
      (when-not (= ::eof value)
        value))))

(defn- run-with-queue [queue opts]
  (let [result        (atom nil)
        output-writer (StringWriter.)
        error-writer  (StringWriter.)]
    (future
      (binding [*in*  (BufferedReader. (StringReader. ""))
                *out* output-writer
                *err* error-writer]
        (reset! result (sut/run (assoc opts :acp-read-line-fn (queued-read-line queue)))))
      {:exit   @result
       :output (str output-writer)
       :stderr (str error-writer)})))

(describe "ACP proxy reconnect"

  (around [it]
    (binding [fs/*fs* (fs/mem-fs)]
      (it)))

  (it "caps the exponential reconnect delay at the configured max"
    (should= 5 (#'sut/reconnect-delay-ms 4 {:acp-proxy-reconnect-delay-ms 1
                                            :acp-proxy-reconnect-max-delay-ms 5})))

  (it "emits ACP-conformant disconnect and reconnect notifications"
    (let [transport (ws/reconnectable-loopback)
          state-dir (str "/test/acp-proxy-reconnect-" (random-uuid))
          queue     (LinkedBlockingQueue.)]
      (storage/create-session! state-dir "s1")
      (let [runner (run-with-queue queue
                                   (assoc base-opts
                                     :remote "ws://test/acp"
                                     :state-dir state-dir
                                     :acp-proxy-reconnect-delay-ms 1
                                     :acp-proxy-reconnect-max-delay-ms 2
                                     :acp-proxy-main-poll-ms 1
                                     :ws-connection-factory (fn [url _] (ws/connect-loopback! transport url))))]
      (ws/accept-loopback! transport)
      (ws/drop-loopback! transport)
      (Thread/sleep 50)
      (ws/restore-loopback! transport)
      (ws/accept-loopback! transport)
      (Thread/sleep 200)
      (.put queue ::eof)
      (let [result (deref runner 2000 ::timeout)]
        (when (= ::timeout result)
          (future-cancel runner))
        (should-not= ::timeout result)
        (let [messages (output-messages (:output result))]
          (should= ["session/update" "session/update"] (mapv :method messages))
          (should= ["s1" "s1"] (mapv #(get-in % [:params :sessionId]) messages))
          (should= ["agent_thought_chunk" "agent_thought_chunk"]
                   (mapv #(get-in % [:params :update :sessionUpdate]) messages))
          (should= ["remote connection lost" "reconnected to remote"]
                   (mapv #(get-in % [:params :update :content :text]) messages)))))))

  (it "returns a JSON-RPC error when a request arrives during reconnect"
    (let [transport (ws/reconnectable-loopback)
          state-dir (str "/test/acp-proxy-disconnect-" (random-uuid))
          queue     (LinkedBlockingQueue.)
          request   (jrpc/request-line 42 "session/prompt" {:sessionId "s1"}
                                      [{:type "text" :text "hello"}])
          runner    (run-with-queue queue
                                    (assoc base-opts
                                      :remote "ws://test/acp"
                                      :state-dir state-dir
                                      :acp-proxy-reconnect-delay-ms 1
                                      :acp-proxy-reconnect-max-delay-ms 2
                                      :acp-proxy-main-poll-ms 1
                                      :ws-connection-factory (fn [url _] (ws/connect-loopback! transport url))))]
      (storage/create-session! state-dir "s1")
      (ws/accept-loopback! transport)
      (ws/drop-loopback-permanently! transport)
      (Thread/sleep 50)
      (.put queue request)
      (Thread/sleep 50)
      (.put queue ::eof)
      (let [result (deref runner 2000 ::timeout)]
        (when (= ::timeout result)
          (future-cancel runner))
        (should-not= ::timeout result)
        (let [messages (output-messages (:output result))
              response (last messages)]
          (should= 42 (:id response))
          (should= -32099 (get-in response [:error :code]))
          (should= "remote connection lost, reconnecting" (get-in response [:error :message])))))))
