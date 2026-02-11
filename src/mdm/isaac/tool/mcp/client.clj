(ns mdm.isaac.tool.mcp.client
  "MCP HTTP transport client - connects to external MCP servers."
  (:require [clojure.data.json :as json]
            [org.httpkit.client :as http])
  (:import [java.util UUID]))

;; Request ID generation

(defn- generate-request-id
  "Generate a unique request ID."
  []
  (str (UUID/randomUUID)))

;; JSON-RPC 2.0 message building

(defn build-request
  "Build a JSON-RPC 2.0 request."
  [method params]
  {:jsonrpc "2.0"
   :id (generate-request-id)
   :method method
   :params params})

;; JSON-RPC 2.0 response parsing

(defn parse-response
  "Parse a JSON-RPC 2.0 response.
   Returns {:status :ok :result ...} or {:status :error :code ... :message ...}"
  [response]
  (if-let [error (:error response)]
    {:status :error
     :code (:code error)
     :message (:message error)}
    {:status :ok
     :result (:result response)}))

;; Client state management

(defn create-client
  "Create a new MCP client for the given endpoint."
  [endpoint]
  {:endpoint endpoint
   :session-id nil
   :state :disconnected})

(defn set-session
  "Set the session ID on the client, transitioning to connected state."
  [client session-id]
  (assoc client
         :session-id session-id
         :state :connected))

;; HTTP headers

(defn build-headers
  "Build HTTP headers for MCP request."
  [client]
  (cond-> {"Content-Type" "application/json"
           "Accept" "application/json, text/event-stream"}
    (:session-id client) (assoc "Mcp-Session-Id" (:session-id client))))

;; MCP Protocol Operations

(def ^:private mcp-protocol-version "2025-03-26")

(defn initialize!
  "Initialize MCP session with server.
   transport-fn: (fn [client request] -> {:body response :headers headers})
   Returns {:status :ok :session-id ... :server-info ...} or {:status :error ...}"
  [client transport-fn]
  (let [request (build-request "initialize"
                               {:protocolVersion mcp-protocol-version
                                :capabilities {}
                                :clientInfo {:name "isaac"
                                             :version "1.0.0"}})
        {:keys [body headers]} (transport-fn client request)
        parsed (parse-response body)]
    (if (= :ok (:status parsed))
      (merge parsed
             {:session-id (get headers "Mcp-Session-Id")
              :server-info (:serverInfo (:result parsed))})
      parsed)))

(defn list-tools!
  "List available tools from MCP server.
   Returns {:status :ok :tools [...]} or {:status :error ...}"
  [client transport-fn]
  (let [request (build-request "tools/list" {})
        {:keys [body]} (transport-fn client request)
        parsed (parse-response body)]
    (if (= :ok (:status parsed))
      {:status :ok
       :tools (:tools (:result parsed))}
      parsed)))

(defn call-tool!
  "Call a tool on the MCP server.
   Returns {:status :ok :content [...]} or {:status :error ...}"
  [client tool-name args transport-fn]
  (let [request (build-request "tools/call"
                               {:name tool-name
                                :arguments args})
        {:keys [body]} (transport-fn client request)
        parsed (parse-response body)]
    (if (= :ok (:status parsed))
      {:status :ok
       :content (:content (:result parsed))}
      parsed)))

;; HTTP Transport

(defn request->json
  "Serialize a request map to JSON string."
  [request]
  (json/write-str request))

(defn json->response
  "Deserialize a JSON string to response map."
  [json-str]
  (json/read-str json-str :key-fn keyword))

(defn- normalize-session-header
  "Extract Mcp-Session-Id from response headers (case-insensitive)."
  [headers]
  (or (get headers "Mcp-Session-Id")
      (get headers "mcp-session-id")
      (get headers :mcp-session-id)))

(defn http-transport
  "HTTP transport function. Sends request to client's endpoint.
   Returns {:body parsed-response :headers normalized-headers}"
  [client request]
  (let [response @(http/post (:endpoint client)
                             {:headers (build-headers client)
                              :body (request->json request)})]
    (if (= 200 (:status response))
      {:body (json->response (:body response))
       :headers {"Mcp-Session-Id" (normalize-session-header (:headers response))}}
      {:body {:error {:code -32000
                      :message (str "HTTP error: " (:status response))}}})))

(defn create-http-transport
  "Create an HTTP transport function.
   Returns a function that can be passed to initialize!, list-tools!, etc."
  []
  http-transport)
