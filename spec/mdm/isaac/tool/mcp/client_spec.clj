(ns mdm.isaac.tool.mcp.client-spec
  "Specs for MCP HTTP transport client.
   Protocol-level specs (JSON-RPC, client state, MCP operations) are in protocol_spec.
   This file tests HTTP-transport-specific behavior and backward compatibility."
  (:require [mdm.isaac.tool.mcp.client :as sut]
            [mdm.isaac.tool.mcp.protocol :as protocol]
            [speclj.core :refer :all]))

(describe "MCP HTTP Client"

  (context "backward compatibility - re-exported protocol functions"

    (it "re-exports build-request"
      (should= protocol/build-request sut/build-request))

    (it "re-exports parse-response"
      (should= protocol/parse-response sut/parse-response))

    (it "re-exports create-client"
      (should= protocol/create-client sut/create-client))

    (it "re-exports set-session"
      (should= protocol/set-session sut/set-session))

    (it "re-exports build-headers"
      (should= protocol/build-headers sut/build-headers))

    (it "re-exports request->json"
      (should= protocol/request->json sut/request->json))

    (it "re-exports json->response"
      (should= protocol/json->response sut/json->response))

    (it "re-exports initialize!"
      (should= protocol/initialize! sut/initialize!))

    (it "re-exports list-tools!"
      (should= protocol/list-tools! sut/list-tools!))

    (it "re-exports call-tool!"
      (should= protocol/call-tool! sut/call-tool!)))

  (context "HTTP transport"

    (it "creates an HTTP transport function"
      (let [transport-fn (sut/create-http-transport)]
        (should-not-be-nil transport-fn)
        (should (fn? transport-fn)))))

  )
