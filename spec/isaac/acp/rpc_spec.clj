(ns isaac.acp.rpc-spec
  (:require
    [cheshire.core :as json]
    [isaac.acp.rpc :as sut]
    [speclj.core :refer :all]))

(describe "ACP JSON-RPC"

  (describe "read-message"

    (it "parses a line-delimited JSON-RPC message"
      (let [reader (java.io.BufferedReader.
                     (java.io.StringReader. "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}\n"))]
        (should= {:jsonrpc "2.0" :id 1 :method "ping"}
                 (sut/read-message reader))))

    (it "returns nil at EOF"
      (let [reader (java.io.BufferedReader. (java.io.StringReader. ""))]
        (should= nil (sut/read-message reader))))

    (it "throws ex-info with -32700 for malformed JSON"
      (let [reader (java.io.BufferedReader. (java.io.StringReader. "{not json}\n"))]
        (try
          (sut/read-message reader)
          (should false)
          (catch clojure.lang.ExceptionInfo e
            (should= -32700 (:code (ex-data e))))))))

  (describe "write-message!"

    (it "writes one JSON object per line and flushes"
      (let [buffer (java.io.StringWriter.)
            writer (java.io.BufferedWriter. buffer)]
        (sut/write-message! writer {:jsonrpc "2.0" :id 12 :result {:ok true}})
        (should= {:jsonrpc "2.0" :id 12 :result {:ok true}}
                 (json/parse-string (.toString buffer) true)))))

  (describe "dispatch"

    (it "routes request by method and returns response"
      (let [handlers {"echo" (fn [params _message] {:text (:text params)})}
            response (sut/dispatch handlers {:jsonrpc "2.0" :id 7 :method "echo" :params {:text "hi"}})]
        (should= {:jsonrpc "2.0" :id 7 :result {:text "hi"}} response)))

    (it "returns nil for notifications"
      (let [called?  (atom false)
            handlers {"notify" (fn [_params _message] (reset! called? true) :ok)}
            response (sut/dispatch handlers {:jsonrpc "2.0" :method "notify" :params {:x 1}})]
        (should @called?)
        (should= nil response)))

    (it "returns -32601 for unknown methods"
      (let [response (sut/dispatch {} {:jsonrpc "2.0" :id 3 :method "missing" :params {}})]
        (should= {:jsonrpc "2.0" :id 3 :error {:code -32601 :message "Method not found"}}
                 response)))

    (it "returns -32602 when handler signals invalid params"
      (let [handlers {"needs" (fn [_params _message]
                                 (throw (ex-info "Invalid params" {:code -32602 :message "Invalid params"})))}
            response (sut/dispatch handlers {:jsonrpc "2.0" :id 9 :method "needs" :params {}})]
        (should= {:jsonrpc "2.0" :id 9 :error {:code -32602 :message "Invalid params"}}
                 response))))

  (describe "handle-line"

    (it "returns -32700 for malformed JSON lines"
      (let [response (sut/handle-line {} "{bad json")]
        (should= {:jsonrpc "2.0" :id nil :error {:code -32700 :message "Parse error"}}
                 response))))

  )
