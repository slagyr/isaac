(ns isaac.acp.jsonrpc-spec
  (:require
    [cheshire.core :as json]
    [isaac.acp.jsonrpc :as jrpc]
    [speclj.core :refer :all]))

(describe "jsonrpc"
  (describe "request"
    (it "builds a JSON-RPC request map without params"
      (should= {:jsonrpc "2.0" :id 1 :method "ping"}
               (jrpc/request 1 "ping")))

    (it "includes params when provided"
      (should= {:jsonrpc "2.0" :id 7 :method "echo" :params {:text "hi"}}
               (jrpc/request 7 "echo" {:text "hi"}))))

  (describe "request-line"
    (it "builds a JSON-RPC request line with trailing newline"
      (should= {:jsonrpc "2.0" :id 1 :method "ping"}
               (json/parse-string (jrpc/request-line 1 "ping") true)))

    (it "includes params when provided"
      (should= {:jsonrpc "2.0" :id 7 :method "echo" :params {:text "hi"}}
               (json/parse-string (jrpc/request-line 7 "echo" {:text "hi"}) true)))

    (it "ends with a newline"
      (should (.endsWith (jrpc/request-line 1 "ping") "\n")))))
