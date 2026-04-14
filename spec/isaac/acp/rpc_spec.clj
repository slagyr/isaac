(ns isaac.acp.rpc-spec
  (:require
    [cheshire.core :as json]
    [isaac.acp.jsonrpc :as jrpc]
    [isaac.acp.rpc :as sut]
    [speclj.core :refer :all])
  (:import
    (clojure.lang ExceptionInfo)
    (java.io BufferedReader BufferedWriter StringReader StringWriter)))

(defn reader-for [line]
  (BufferedReader. (StringReader. line)))

(describe "ACP JSON-RPC"

  (describe "read-message"

    (it "parses a line-delimited JSON-RPC message"
      (let [reader (reader-for (jrpc/request-line 1 "ping"))]
        (should= (jrpc/request 1 "ping") (sut/read-message reader))))

    (it "returns nil at EOF"
      (let [reader (reader-for "")]
        (should= nil (sut/read-message reader))))

    (it "throws ex-info with PARSE_ERROR for malformed JSON"
      (let [reader (reader-for "{not json}\n")]
        (try
          (sut/read-message reader)
          (should-fail "Expected malformed JSON to throw ExceptionInfo")
          (catch ExceptionInfo e
            (should= jrpc/PARSE_ERROR (:code (ex-data e))))))))

  (describe "write-message!"

    (it "writes one JSON object per line and flushes"
      (let [buffer (StringWriter.)
            writer (BufferedWriter. buffer)]
        (sut/write-message! writer (jrpc/result 12 {:ok true}))
        (should= (jrpc/result 12 {:ok true}) (json/parse-string (.toString buffer) true)))))

  (describe "dispatch"

    (it "routes request by method and returns response"
      (let [handlers {"echo" (fn [params _message] {:text (:text params)})}
            response (sut/dispatch handlers (jrpc/request 7 "echo" {:text "hi"}))]
        (should= (jrpc/result 7 {:text "hi"}) response)))

    (it "returns nil for notifications"
      (let [called?  (atom false)
            handlers {"notify" (fn [_params _message] (reset! called? true) :ok)}
            response (sut/dispatch handlers (jrpc/notification "notify" {:x 1}))]
        (should @called?)
        (should= nil response)))

    (it "returns unknown method error"
      (let [response (sut/dispatch {} (jrpc/request 3 "missing" {}))]
        (should= (jrpc/method-not-found 3) response)))

    (it "propagates invalid params failures to the caller"
      (let [handlers {"needs" (fn [_params _message]
                                 (throw (ex-info "Params did not match schema"
                                                 {:type :invalid-params
                                                  :message "Params did not match schema"})))}]
        (should-throw clojure.lang.ExceptionInfo
                      (sut/dispatch handlers (jrpc/request 9 "needs" {})))))

    (it "supports handlers returning response with notifications"
      (let [handlers {"stream" (fn [_params _message]
                                  {:result {:stopReason "end_turn"}
                                   :notifications [{:jsonrpc "2.0" :method "session/update"}]})}
            response (sut/dispatch handlers (jrpc/request 20 "stream" {}))]
        (should= (jrpc/result 20 {:stopReason "end_turn"})
                 (:response response))
        (should= [{:jsonrpc "2.0" :method "session/update"}]
                 (:notifications response)))))

  (describe "handle-line"

    (it "returns PARSE_ERROR for malformed JSON lines"
      (let [response (sut/handle-line {} "{bad json")]
        (should= {:jsonrpc "2.0" :id nil :error {:code jrpc/PARSE_ERROR :message "Parse error"}}
                 response)))

    (it "translates domain invalid params failures into JSON-RPC invalid params"
      (let [handlers {"needs" (fn [_params _message]
                                 (throw (ex-info "Params did not match schema"
                                                 {:type :invalid-params
                                                  :message "Params did not match schema"})))}
            response (sut/handle-line handlers (jrpc/request-line 9 "needs" {}))]
        (should= (jrpc/invalid-params 9) response)))

    (it "translates illegal argument failures into JSON-RPC invalid params"
      (let [handlers {"needs" (fn [_params _message]
                                 (throw (IllegalArgumentException. "Expected map params")))}
            response (sut/handle-line handlers (jrpc/request-line 9 "needs" {}))]
        (should= (jrpc/invalid-params 9) response))))

  )
