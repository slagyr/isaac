(ns isaac.util.jsonrpc-spec
  (:require
    [cheshire.core :as json]
    [isaac.util.jsonrpc :as jrpc]
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
    (it "builds a JSON-RPC request line"
      (should= {:jsonrpc "2.0" :id 1 :method "ping"}
               (json/parse-string (jrpc/request-line 1 "ping") true)))

    (it "includes params when provided"
      (should= {:jsonrpc "2.0" :id 7 :method "echo" :params {:text "hi"}}
               (json/parse-string (jrpc/request-line 7 "echo" {:text "hi"}) true)))

    (it "ends with a newline"
      (should (.endsWith (jrpc/request-line 1 "ping") "\n"))))

  (describe "notification"
    (it "omits :id"
      (should= {:jsonrpc "2.0" :method "tick"}
               (jrpc/notification "tick")))

    (it "carries params when provided"
      (should= {:jsonrpc "2.0" :method "tick" :params {:n 1}}
               (jrpc/notification "tick" {:n 1}))))

  (describe "predicates"
    (it "notification? is true when :id is missing"
      (should (jrpc/notification? {:jsonrpc "2.0" :method "tick"}))
      (should-not (jrpc/notification? {:jsonrpc "2.0" :id 1 :method "ping"})))

    (it "result? requires :id and :result"
      (should (jrpc/result? {:jsonrpc "2.0" :id 1 :result {:ok true}}))
      (should-not (jrpc/result? {:jsonrpc "2.0" :id 1 :error {:code -1}}))
      (should-not (jrpc/result? {:jsonrpc "2.0" :result {:ok true}})))

    (it "error? requires :jsonrpc and :error"
      (should (jrpc/error? {:jsonrpc "2.0" :id 1 :error {:code -1}}))
      (should-not (jrpc/error? {:jsonrpc "2.0" :id 1 :result {}}))))

  (describe "parse-message"
    (it "parses a JSON line into a message map"
      (should= {:jsonrpc "2.0" :id 1 :method "ping"}
               (jrpc/parse-message "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}")))

    (it "returns the parse-error marker on malformed input"
      (should (jrpc/parse-error? (jrpc/parse-message "{not json"))))))
