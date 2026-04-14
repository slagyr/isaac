(ns isaac.cli.acp-spec
  (:require
    [clojure.string :as str]
    [isaac.acp.rpc :as rpc]
    [isaac.acp.ws :as ws]
    [isaac.cli.acp :as sut]
    [isaac.logger :as log]
    [isaac.session.fs :as fs]
    [isaac.session.storage :as storage]
    [speclj.core :refer :all]))

(def base-opts
  {:state-dir "target/test-acp"
   :agents    {"main" {:name "main" :soul "You are Isaac." :model "grover"}}
   :models    {"grover" {:alias "grover" :model "echo" :provider "grover" :contextWindow 32768}}})

(defn- run-with-stdin [content opts]
  (binding [*in* (java.io.BufferedReader. (java.io.StringReader. content))]
    (let [result (atom nil)
          output-writer (java.io.StringWriter.)
          error-writer  (java.io.StringWriter.)]
      (binding [*out* output-writer
                *err* error-writer]
        (reset! result (sut/run opts)))
      {:output (str output-writer)
       :stderr (str error-writer)
       :exit   @result})))

(describe "ACP CLI"

  (around [it] (binding [fs/*fs* (fs/mem-fs)] (it)))

  (it "returns 0 when stdin is empty"
    (should= 0 (:exit (run-with-stdin "" base-opts))))

  (it "writes JSON response to stdout for each request"
    (with-redefs [rpc/handle-line (fn [_ _] {:jsonrpc "2.0" :id 1 :result {:ok true}})]
      (let [{:keys [output exit]} (run-with-stdin "{}\n" base-opts)]
        (should= 0 exit)
        (should (str/includes? output "\"id\":1")))))

  (it "processes multiple requests in sequence"
    (let [call-count (atom 0)]
      (with-redefs [rpc/handle-line (fn [_ _]
                                      (swap! call-count inc)
                                      {:jsonrpc "2.0" :id @call-count :result {}})]
        (run-with-stdin "{}\n{}\n" base-opts)
        (should= 2 @call-count))))

  (it "skips nil responses (notifications with no response)"
    (with-redefs [rpc/handle-line (fn [_ _] nil)]
      (let [{:keys [output exit]} (run-with-stdin "{}\n" base-opts)]
        (should= 0 exit)
        (should= "" (str/trim output)))))

  (it "writes notification messages before the response in an envelope"
    (let [notif {:jsonrpc "2.0" :method "progress" :params {}}
          resp  {:jsonrpc "2.0" :id 1 :result {}}]
      (with-redefs [rpc/handle-line (fn [_ _] {:response resp :notifications [notif]})]
        (let [{:keys [output]} (run-with-stdin "{}\n" base-opts)]
          (should (str/includes? output "progress"))
          (should (str/includes? output "\"id\":1"))))))

  (it "prints a ready signal to stderr on startup"
    (let [{:keys [stderr exit]} (run-with-stdin "" base-opts)]
      (should= 0 exit)
      (should (str/includes? stderr "isaac acp ready"))))

  (it "logs inbound method names when --verbose is enabled"
    (let [{:keys [stderr exit]} (run-with-stdin "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":1}}\n"
                                              (assoc base-opts :verbose true))]
      (should= 0 exit)
      (should (str/includes? stderr "initialize"))))

  (it "returns the attached session key for session/new when --session exists"
    (let [state-dir    (str "target/test-acp-attached-" (random-uuid))
          session-key  "agent:main:acp:direct:user1"
          _            (storage/create-session! state-dir session-key)
          request      "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"session/new\",\"params\":{}}\n"
          {:keys [output exit]} (run-with-stdin request (assoc base-opts :state-dir state-dir :session session-key))]
      (should= 0 exit)
      (should (str/includes? output "\"sessionId\":\"user1\""))))

  (it "fails when --session session does not exist"
    (let [missing "agent:main:acp:direct:nonexistent"
          {:keys [stderr exit]} (run-with-stdin "" (assoc base-opts :session missing :state-dir "target/test-acp-missing"))]
      (should= 1 exit)
      (should (str/includes? stderr "session not found"))
      (should (str/includes? stderr missing))))

  (it "uses --agent when creating a new session"
    (let [opts                (assoc base-opts :agent "bosun"
                                     :agents {"main" {:name "main" :soul "You are Isaac." :model "grover"}
                                               "bosun" {:name "bosun" :soul "You are a pirate." :model "grover"}})
          request             "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"session/new\",\"params\":{}}\n"
          {:keys [output exit]} (run-with-stdin request opts)]
      (should= 0 exit)
      (should (str/includes? output "sessionId"))))

  (it "fails when --model alias is unknown"
    (let [{:keys [stderr exit]} (run-with-stdin "" (assoc base-opts :model "nonexistent"))]
      (should= 1 exit)
      (should (str/includes? stderr "unknown model"))
      (should (str/includes? stderr "nonexistent"))))

  (it "proxies requests over a remote websocket connection"
    (let [{:keys [client server]} (ws/loopback-pair)
          request                 "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":1}}\n"
          response*               (future
                                    (let [line (ws/ws-receive! server 100)]
                                      (when line
                                        (ws/ws-send! server "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":1}}"))))
          {:keys [output exit]}   (run-with-stdin request
                                                  (assoc base-opts
                                                    :remote "ws://test/acp"
                                                    :ws-connection-factory (fn [_ _] client)))]
      @response*
      (should= 0 exit)
      (should (str/includes? output "\"id\":1"))))

  (it "fails with a clear error when the remote connection cannot be opened"
    (let [{:keys [stderr exit]} (run-with-stdin ""
                                                (assoc base-opts
                                                  :remote "ws://localhost:9999/acp"
                                                  :ws-connection-factory (fn [_ _]
                                                                           (throw (ex-info "boom" {})))))]
      (should= 1 exit)
      (should (str/includes? stderr "could not connect"))))

  (it "uses the most recent session when --resume is set"
    (let [state-dir    (str "target/test-acp-resume-" (random-uuid))
          older        "older"
          recent       "recent"
          _            (storage/create-session! state-dir older)
          _            (storage/create-session! state-dir recent)
          _            (storage/update-session! state-dir older {:updatedAt "2026-04-10T10:00:00"})
          _            (storage/update-session! state-dir recent {:updatedAt "2026-04-12T15:00:00"})
          request      "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"session/new\",\"params\":{}}\n"
          {:keys [output exit]} (run-with-stdin request (assoc base-opts :state-dir state-dir :resume true))]
      (should= 0 exit)
      (should= recent (get-in (cheshire.core/parse-string output true) [:result :sessionId]))))

  (it "rejects combining --resume with --model"
    (let [{:keys [stderr exit]} (run-with-stdin "" (assoc base-opts :resume true :model "grover"))]
      (should= 1 exit)
      (should (str/includes? stderr "cannot combine --resume with --model"))))

  (it "adds model and agent query params when proxying to a remote server"
    (let [captured-url (atom nil)
          {:keys [exit]} (run-with-stdin ""
                                         (assoc base-opts
                                           :remote "ws://test/acp"
                                           :agent "ketch"
                                           :model "grover2"
                                           :ws-connection-factory (fn [url _]
                                                                    (reset! captured-url url)
                                                                    (reify ws/WsConnection
                                                                      (ws-send! [_ _] nil)
                                                                      (ws-receive! [_] nil)
                                                                      (ws-receive! [_ _] nil)
                                                                      (ws-close! [_] nil)))))]
      (should= 0 exit)
      (should= "ws://test/acp?model=grover2&agent=ketch" @captured-url)))

  (it "adds resume query param when proxying to a remote server"
    (let [captured-url (atom nil)
          {:keys [exit]} (run-with-stdin ""
                                         (assoc base-opts
                                           :remote "ws://test/acp"
                                           :resume true
                                           :ws-connection-factory (fn [url _]
                                                                    (reset! captured-url url)
                                                                    (reify ws/WsConnection
                                                                      (ws-send! [_ _] nil)
                                                                      (ws-receive! [_] nil)
                                                                      (ws-receive! [_ _] nil)
                                                                      (ws-close! [_] nil)))))]
      (should= 0 exit)
      (should= "ws://test/acp?resume=true" @captured-url)))

  (it "logs proxy lifecycle and forwarded initialize requests"
    (let [{:keys [client server]} (ws/loopback-pair)
          request                 "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":1}}\n"
          response*               (future
                                    (let [line (ws/ws-receive! server 100)]
                                      (when line
                                        (ws/ws-send! server "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":1}}"))))]
      (log/capture-logs
        (let [{:keys [exit]} (run-with-stdin request
                                             (assoc base-opts
                                               :remote "ws://test/acp"
                                               :ws-connection-factory (fn [_ _] client)))]
          @response*
          (should= 0 exit)
          (should= [:acp-proxy/connected :acp-proxy/initialize :acp-proxy/disconnected]
                   (mapv :event @log/captured-logs))))))

  (it "reconnects after a dropped remote connection and emits status notifications"
    (let [transport (ws/reconnectable-loopback)
          output*   (atom nil)
          err*      (atom nil)
          exit*     (atom nil)
          request-1 "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":1}}\n"
          request-2 "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"initialize\",\"params\":{\"protocolVersion\":1}}\n"
          server*   (future
                      (let [server-1 (ws/accept-loopback! transport)]
                        (should= request-1 (str (ws/ws-receive! server-1 100) "\n"))
                        (ws/ws-send! server-1 "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":1}}")
                        (Thread/sleep 1)
                        (ws/drop-loopback! transport)
                        (ws/restore-loopback! transport)
                        (let [server-2 (ws/accept-loopback! transport)]
                          (should= request-2 (str (ws/ws-receive! server-2 200) "\n"))
                          (ws/ws-send! server-2 "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"protocolVersion\":1}}")
                          (ws/ws-close! server-2))))
          runner*   (future
                      (let [{:keys [output stderr exit]} (run-with-stdin (str request-1 request-2)
                                                                        (assoc base-opts
                                                                          :remote "ws://test/acp"
                                                                          :acp-proxy-max-reconnects 3
                                                                          :acp-proxy-reconnect-delay-ms 1
                                                                          :ws-connection-factory (fn [url _] (ws/connect-loopback! transport url))))]
                        (reset! output* output)
                        (reset! err* stderr)
                        (reset! exit* exit)))]
      @server*
      @runner*
      (should= 0 @exit*)
      (should (str/includes? @output* "Connection lost"))
      (should (str/includes? @output* "Reconnecting"))
      (should (str/includes? @output* "Reconnected"))
      (should (str/includes? @output* "\"id\":2"))))

  (it "gives up after max reconnect attempts"
    (let [transport (ws/reconnectable-loopback)
          request   "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":1}}\n"
          server*   (future
                      (let [server-1 (ws/accept-loopback! transport)]
                        (should= request (str (ws/ws-receive! server-1 100) "\n"))
                        (ws/ws-send! server-1 "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":1}}")
                        (ws/drop-loopback-permanently! transport)))]
      (let [{:keys [output stderr exit]} (run-with-stdin request
                                                         (assoc base-opts
                                                           :remote "ws://test/acp"
                                                           :acp-proxy-max-reconnects 2
                                                           :acp-proxy-reconnect-delay-ms 1
                                                           :ws-connection-factory (fn [url _] (ws/connect-loopback! transport url))))]
        @server*
        (should= 1 exit)
        (should (str/includes? output "Connection lost"))
        (should (str/includes? stderr "gave up reconnecting")))))

  )
