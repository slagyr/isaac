(ns mdm.isaac.tool.mcp.client-spec
  "Specs for MCP HTTP transport client."
  (:require [clojure.data.json :as json]
            [mdm.isaac.tool.mcp.client :as sut]
            [org.httpkit.client :as http]
            [speclj.core :refer :all]))

(describe "MCP HTTP Client"

  (context "JSON-RPC request building"

    (it "builds a valid JSON-RPC 2.0 request"
      (let [request (sut/build-request "initialize" {:protocolVersion "2025-03-26"})]
        (should= "2.0" (:jsonrpc request))
        (should= "initialize" (:method request))
        (should= {:protocolVersion "2025-03-26"} (:params request))
        (should-not-be-nil (:id request))))

    (it "generates unique request IDs"
      (let [req1 (sut/build-request "test" {})
            req2 (sut/build-request "test" {})]
        (should-not= (:id req1) (:id req2)))))

  (context "JSON-RPC response parsing"

    (it "extracts result from successful response"
      (let [response {:jsonrpc "2.0"
                      :id "123"
                      :result {:capabilities {:tools {}}}}
            parsed (sut/parse-response response)]
        (should= :ok (:status parsed))
        (should= {:capabilities {:tools {}}} (:result parsed))))

    (it "extracts error from error response"
      (let [response {:jsonrpc "2.0"
                      :id "123"
                      :error {:code -32600
                              :message "Invalid Request"}}
            parsed (sut/parse-response response)]
        (should= :error (:status parsed))
        (should= -32600 (:code parsed))
        (should= "Invalid Request" (:message parsed)))))

  (context "client state management"

    (it "creates a new client with endpoint"
      (let [client (sut/create-client "https://api.example.com/mcp")]
        (should= "https://api.example.com/mcp" (:endpoint client))
        (should-be-nil (:session-id client))
        (should= :disconnected (:state client))))

    (it "updates client with session ID after initialization"
      (let [client (sut/create-client "https://api.example.com/mcp")
            updated (sut/set-session client "session-123")]
        (should= "session-123" (:session-id updated))
        (should= :connected (:state updated)))))

  (context "HTTP request headers"

    (it "builds headers for initial request"
      (let [client (sut/create-client "https://api.example.com/mcp")
            headers (sut/build-headers client)]
        (should= "application/json" (get headers "Content-Type"))
        (should= "application/json, text/event-stream" (get headers "Accept"))))

    (it "includes session ID in headers when available"
      (let [client (-> (sut/create-client "https://api.example.com/mcp")
                       (sut/set-session "session-456"))
            headers (sut/build-headers client)]
        (should= "session-456" (get headers "Mcp-Session-Id")))))

  (context "MCP protocol operations"

    (with-stubs)

    (it "initialize! sends initialize request and sets session"
      (let [client (sut/create-client "https://api.example.com/mcp")
            mock-response {:jsonrpc "2.0"
                           :id "123"
                           :result {:protocolVersion "2025-03-26"
                                    :capabilities {:tools {}}
                                    :serverInfo {:name "test-server"
                                                 :version "1.0.0"}}}
            mock-transport (stub :transport {:return {:body mock-response
                                                      :headers {"Mcp-Session-Id" "sess-abc"}}})]
        (let [result (sut/initialize! client mock-transport)]
          (should-have-invoked :transport)
          (should= :ok (:status result))
          (should= "sess-abc" (:session-id result))
          (should= "test-server" (get-in result [:server-info :name])))))

    (it "list-tools! sends tools/list request"
      (let [client (-> (sut/create-client "https://api.example.com/mcp")
                       (sut/set-session "sess-123"))
            mock-response {:jsonrpc "2.0"
                           :id "456"
                           :result {:tools [{:name "brave_search"
                                             :description "Search the web"
                                             :inputSchema {:type "object"}}]}}
            mock-transport (stub :transport {:return {:body mock-response}})]
        (let [result (sut/list-tools! client mock-transport)]
          (should-have-invoked :transport)
          (should= :ok (:status result))
          (should= 1 (count (:tools result)))
          (should= "brave_search" (-> result :tools first :name)))))

    (it "call-tool! invokes a tool with arguments"
      (let [client (-> (sut/create-client "https://api.example.com/mcp")
                       (sut/set-session "sess-123"))
            mock-response {:jsonrpc "2.0"
                           :id "789"
                           :result {:content [{:type "text"
                                               :text "Search results here"}]}}
            mock-transport (stub :transport {:return {:body mock-response}})]
        (let [result (sut/call-tool! client "brave_search" {:query "clojure"} mock-transport)]
          (should-have-invoked :transport)
          (should= :ok (:status result))
          (should= "text" (-> result :content first :type))))))

  (context "HTTP transport"

    (it "serializes request to JSON"
      (let [request {:jsonrpc "2.0" :id "123" :method "test" :params {}}
            json-str (sut/request->json request)]
        (should-contain "\"jsonrpc\":\"2.0\"" json-str)
        (should-contain "\"method\":\"test\"" json-str)))

    (it "deserializes JSON response"
      (let [json-str "{\"jsonrpc\":\"2.0\",\"id\":\"123\",\"result\":{\"ok\":true}}"
            response (sut/json->response json-str)]
        (should= "2.0" (:jsonrpc response))
        (should= "123" (:id response))
        (should= {:ok true} (:result response))))

    (it "creates an HTTP transport function"
      (let [transport-fn (sut/create-http-transport)]
        (should-not-be-nil transport-fn)
        (should (fn? transport-fn)))))

  )
