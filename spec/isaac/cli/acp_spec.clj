(ns isaac.cli.acp-spec
  (:require
    [clojure.string :as str]
    [isaac.acp.rpc :as rpc]
    [isaac.acp.ws :as ws]
    [isaac.cli.acp :as sut]
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
    (let [state-dir    "target/test-acp-attached"
          session-key  "agent:main:acp:direct:user1"
          _            (storage/create-session! state-dir session-key)
          request      "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"session/new\",\"params\":{}}\n"
          {:keys [output exit]} (run-with-stdin request (assoc base-opts :state-dir state-dir :session session-key))]
      (should= 0 exit)
      (should (str/includes? output (str "\"sessionId\":\"" session-key "\"")))))

  (it "fails when --session session does not exist"
    (let [missing "agent:main:acp:direct:nonexistent"
          {:keys [stderr exit]} (run-with-stdin "" (assoc base-opts :session missing :state-dir "target/test-acp-missing"))]
      (should= 1 exit)
      (should (str/includes? stderr "session not found"))
      (should (str/includes? stderr missing))))

  (it "uses --agent for session/new keys"
    (let [opts                (assoc base-opts :agent "bosun"
                                     :agents {"main" {:name "main" :soul "You are Isaac." :model "grover"}
                                              "bosun" {:name "bosun" :soul "You are a pirate." :model "grover"}})
          request             "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"session/new\",\"params\":{}}\n"
          {:keys [output exit]} (run-with-stdin request opts)]
      (should= 0 exit)
      (should (str/includes? output "\"sessionId\":\"agent:bosun:acp:direct:"))))

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
      (should (str/includes? stderr "could not connect")))))
