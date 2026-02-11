(ns mdm.isaac.tool.mcp.client
  "MCP HTTP transport client - connects to external MCP servers.
   Delegates JSON-RPC and protocol operations to mdm.isaac.tool.mcp.protocol."
  (:require [mdm.isaac.tool.mcp.protocol :as protocol])
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]
           [java.time Duration]))

;; Re-export protocol functions for backward compatibility

(def build-request protocol/build-request)
(def parse-response protocol/parse-response)
(def create-client protocol/create-client)
(def set-session protocol/set-session)
(def build-headers protocol/build-headers)
(def request->json protocol/request->json)
(def json->response protocol/json->response)
(def initialize! protocol/initialize!)
(def list-tools! protocol/list-tools!)
(def call-tool! protocol/call-tool!)

;; HTTP Transport

(defonce ^:private http-client
  (-> (HttpClient/newBuilder)
      (.connectTimeout (Duration/ofSeconds 30))
      (.build)))

(defn- extract-session-header
  "Extract Mcp-Session-Id from Java HttpHeaders (case-insensitive)."
  [headers]
  (-> headers
      (.firstValue "Mcp-Session-Id")
      (.orElse nil)))

(defn http-transport
  "HTTP transport function. Sends request to client's endpoint.
   Returns {:body parsed-response :headers normalized-headers}"
  [client request]
  (let [headers (protocol/build-headers client)
        http-request (-> (HttpRequest/newBuilder)
                         (.uri (URI. (:endpoint client)))
                         (.timeout (Duration/ofSeconds 60))
                         (.header "Content-Type" (get headers "Content-Type"))
                         (.header "Accept" (get headers "Accept"))
                         (cond-> (:session-id client)
                           (.header "Mcp-Session-Id" (:session-id client)))
                         (.POST (HttpRequest$BodyPublishers/ofString (protocol/request->json request)))
                         (.build))
        response (.send http-client http-request (HttpResponse$BodyHandlers/ofString))
        status (.statusCode response)]
    (if (= 200 status)
      {:body (protocol/json->response (.body response))
       :headers {"Mcp-Session-Id" (extract-session-header (.headers response))}}
      {:body {:error {:code -32000
                      :message (str "HTTP error: " status)}}})))

(defn create-http-transport
  "Create an HTTP transport function.
   Returns a function that can be passed to initialize!, list-tools!, etc."
  []
  http-transport)
