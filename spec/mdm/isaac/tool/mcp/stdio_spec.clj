(ns mdm.isaac.tool.mcp.stdio-spec
  "Specs for MCP stdio transport client."
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [mdm.isaac.tool.mcp.protocol :as protocol]
            [mdm.isaac.tool.mcp.stdio :as sut]
            [speclj.core :refer :all]))

(describe "MCP Stdio Transport"

  (context "process creation"

    (it "creates a process config from a command"
      (let [config (sut/process-config ["npx" "@anthropic/mcp-server-filesystem" "/tmp"])]
        (should= ["npx" "@anthropic/mcp-server-filesystem" "/tmp"] (:command config))
        (should= :stopped (:state config))))

    (it "creates a process config with environment variables"
      (let [config (sut/process-config ["node" "server.js"] {:env {"API_KEY" "secret"}})]
        (should= {"API_KEY" "secret"} (:env config)))))

  (context "process lifecycle"

    (it "starts a process and transitions to running state"
      (let [config (sut/process-config ["cat"])
            process (sut/start! config)]
        (try
          (should= :running (:state process))
          (should-not-be-nil (:process process))
          (should-not-be-nil (:stdin process))
          (should-not-be-nil (:stdout process))
          (finally
            (sut/stop! process)))))

    (it "stops a running process"
      (let [config (sut/process-config ["cat"])
            process (sut/start! config)
            stopped (sut/stop! process)]
        (should= :stopped (:state stopped))
        (should-be-nil (:process stopped))))

    (it "returns error when starting an invalid command"
      (let [config (sut/process-config ["nonexistent-command-xyz"])
            result (sut/start! config)]
        (should= :error (:state result))
        (should-not-be-nil (:error result)))))

  (context "message framing"

    (it "writes a JSON-RPC message as a newline-delimited JSON line"
      (let [request {:jsonrpc "2.0" :id "123" :method "test" :params {}}
            framed (sut/frame-message request)]
        (should-contain "\n" framed)
        (should= request (json/read-str (str/trim framed) :key-fn keyword))))

    (it "reads a JSON-RPC response from a line"
      (let [json-line "{\"jsonrpc\":\"2.0\",\"id\":\"123\",\"result\":{\"ok\":true}}\n"
            parsed (sut/parse-message json-line)]
        (should= "2.0" (:jsonrpc parsed))
        (should= {:ok true} (:result parsed)))))

  (context "stdio transport function"

    (it "sends request and receives response via stdio"
      (let [config (sut/process-config ["cat"])
            process (sut/start! config)
            transport-fn (sut/create-stdio-transport process)]
        (try
          ;; cat echoes back what we send, simulating a server
          (let [client (protocol/create-client "stdio://cat")
                request (protocol/build-request "test" {:foo "bar"})
                result (transport-fn client request)]
            (should= :ok (-> result :body protocol/parse-response :status))
            (should= "2.0" (-> result :body :jsonrpc)))
          (finally
            (sut/stop! process)))))

    (it "returns error when process is not running"
      (let [config (sut/process-config ["cat"])
            stopped-process (assoc config :state :stopped)
            transport-fn (sut/create-stdio-transport stopped-process)]
        (let [client (protocol/create-client "stdio://test")
              request (protocol/build-request "test" {})
              result (transport-fn client request)]
          (should= -32000 (-> result :body :error :code))
          (should-not-be-nil (-> result :body :error :message))))))

  (context "integration with MCP protocol operations"

    (it "provides transport-fn compatible with protocol/initialize!"
      (let [config (sut/process-config ["cat"])
            process (sut/start! config)
            transport-fn (sut/create-stdio-transport process)]
        (try
          (let [client (protocol/create-client "stdio://cat")
                ;; cat echoes our request back; parse-response sees no :result or :error
                ;; so it returns {:status :ok :result nil} - verifies plumbing works
                result (protocol/initialize! client transport-fn)]
            (should= :ok (:status result)))
          (finally
            (sut/stop! process))))))

  )
