(ns isaac.acp.rpc-spec
  (:require
    [cheshire.core :as json]
    [isaac.acp.jsonrpc :as jrpc]
    [isaac.acp.rpc :as sut]
    [speclj.core :refer :all])
  (:import
    (java.io BufferedWriter StringWriter)))

(describe "ACP JSON-RPC"

  (context "write-message!"

    (it "writes one JSON object per line and flushes"
      (let [buffer (StringWriter.)
            writer (BufferedWriter. buffer)]
        (sut/write-message! writer (jrpc/result 12 {:ok true}))
        (should= (jrpc/result 12 {:ok true}) (json/parse-string (.toString buffer) true)))))

  (context "dispatch"

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

    (it "ignores unknown notifications"
      (let [response (sut/dispatch {} (jrpc/notification "missing" {}))]
        (should= nil response)))

    (it "returns handler-thrown domain invalid params failures"
      (let [failure  (jrpc/invalid-params 9)
            handlers {"needs" (fn [_params _message] failure)}]
        (should= failure
                 (sut/dispatch handlers (jrpc/request 9 "needs" {})))))

    (it "wraps domain-level maps with :error key (no :jsonrpc) as a normal JSON-RPC result"
      (let [domain-error {:stopReason "error" :error "quota exceeded"}
            handlers     {"prompt" (fn [_params _message] domain-error)}
            response     (sut/dispatch handlers (jrpc/request 10 "prompt" {}))]
        (should= (jrpc/result 10 domain-error) response)))

    (it "maps IllegalArgumentException from handlers to invalid params"
      (let [handlers {"needs" (fn [_params _message]
                                 (throw (IllegalArgumentException. "bad params")))}]
        (should= (jrpc/invalid-params 9)
                 (sut/dispatch handlers (jrpc/request 9 "needs" {})))))

    (it "supports handlers returning response with notifications"
      (let [handlers {"stream" (fn [_params _message]
                                  {:result {:stopReason "end_turn"}
                                   :notifications [{:jsonrpc "2.0" :method "session/update"}]})}
            response (sut/dispatch handlers (jrpc/request 20 "stream" {}))]
        (should= [{:jsonrpc "2.0" :method "session/update"}] (:notifications response))
        (should= (jrpc/result 20 {:stopReason "end_turn"}) (:response response))))

    (it "supports handlers returning notifications without a final response"
      (let [handlers {"stream" (fn [_params _message]
                                  {:notifications [(jrpc/notification "session/update" {:step 1})
                                                   (jrpc/notification "session/update" {:step 2})]})}
            response (sut/dispatch handlers (jrpc/request 20 "stream" {}))]
        (should= {:notifications [(jrpc/notification "session/update" {:step 1})
                                  (jrpc/notification "session/update" {:step 2})]}
                 response)))

    (it "ignores envelope result and notifications for notifications with no payloads"
      (let [handlers {"notify" (fn [_params _message]
                                  {:response (jrpc/result 99 :ignored)
                                   :notifications []})}
            response (sut/dispatch handlers (jrpc/notification "notify" {}))]
        (should= nil response)))

    (it "returns notifications only for notification envelopes"
      (let [handlers {"notify" (fn [_params _message]
                                  {:response (jrpc/result 99 :ignored)
                                   :notifications [(jrpc/notification "session/update" {:ok true})]})}
            response (sut/dispatch handlers (jrpc/notification "notify" {}))]
        (should= {:notifications [(jrpc/notification "session/update" {:ok true})]}
                 response))))

  (context "handle-line"

    (it "returns PARSE_ERROR for malformed JSON lines"
      (let [response (sut/handle-line {} "{bad json")]
        (should= (jrpc/parse-error) response)))

    (it "returns domain invalid params failures as JSON-RPC invalid params"
      (let [handlers {"needs" (fn [_params _message] (jrpc/invalid-params 9))}
            response (sut/handle-line handlers (jrpc/request-line 9 "needs" {}))]
        (should= (jrpc/invalid-params 9) response)))

    (it "returns invalid params from one-arity handlers as JSON-RPC invalid params"
      (let [handlers {"needs" (fn [_params] (jrpc/invalid-params 9))}
            response (sut/handle-line handlers (jrpc/request-line 9 "needs" {}))]
        (should= (jrpc/invalid-params 9) response)))

    (it "returns domain invalid request failures raised by dispatch as JSON-RPC invalid request"
      (let [response (sut/handle-line {} "[]")]
        (should= (jrpc/invalid-request nil) response)))

    (it "preserves request id when returning domain invalid request failures as JSON-RPC invalid request"
      (let [handlers {"broken" (fn [_params _message] (jrpc/invalid-request 11))}
            response (sut/handle-line handlers (jrpc/request-line 11 "broken" {}))]
        (should= (jrpc/invalid-request 11) response)))

    (it "ignores legacy protocol-coded invalid request exception data"
      (let [handlers {"broken" (fn [_params _message] (throw (ex-info "blah" {:code jrpc/INVALID_REQUEST})))}
            response (sut/handle-line handlers (jrpc/request-line nil "broken" {}))]
        (should= (jrpc/internal-error nil) response)))

    (it "ignores legacy protocol-coded invalid params exception data and uses domain failures instead"
      (let [handlers {"needs" (fn [_params _message] (throw (ex-info "blah" {:code jrpc/INVALID_PARAMS})))}
            response (sut/handle-line handlers (jrpc/request-line 9 "needs" {}))]
        (should= (jrpc/internal-error 9) response))))

  )
