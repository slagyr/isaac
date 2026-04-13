(ns isaac.server.acp-websocket-spec
  (:require
    [cheshire.core :as json]
    [isaac.logger :as log]
    [isaac.server.acp-websocket :as sut]
    [org.httpkit.server :as httpkit]
    [speclj.core :refer :all]))

(describe "ACP WebSocket endpoint"

  (describe "auth-error-response"

    (it "returns nil when token auth is not enabled"
      (should= nil (sut/auth-error-response {:cfg {}} {:headers {}})))

    (it "returns 401 when the bearer token is missing"
      (let [response (sut/auth-error-response {:cfg {:gateway {:auth {:mode "token" :token "secret123"}}}}
                                              {:headers {}})]
        (should= 401 (:status response))
        (should= "authentication failed" (:body response))))

    (it "returns nil when the bearer token matches"
      (should= nil (sut/auth-error-response {:cfg {:gateway {:auth {:mode "token" :token "secret123"}}}}
                                            {:headers {"authorization" "Bearer secret123"}}))))

  (describe "send-dispatch-result!"

    (it "sends a response as one JSON line"
      (let [sent (atom [])]
        (sut/send-dispatch-result! #(swap! sent conj %) {:jsonrpc "2.0" :id 1 :result {:ok true}})
        (should= {:jsonrpc "2.0" :id 1 :result {:ok true}}
                 (json/parse-string (first @sent) true))))

    (it "sends notifications before the response when given an envelope"
      (let [sent   (atom [])
            notif  {:jsonrpc "2.0" :method "session/update" :params {:sessionId "agent:main:acp:direct:user1"}}
            result {:notifications [notif]
                    :response      {:jsonrpc "2.0" :id 2 :result {:stopReason "end_turn"}}}]
        (sut/send-dispatch-result! #(swap! sent conj %) result)
        (should= notif (json/parse-string (first @sent) true))
        (should= 2 (:id (json/parse-string (second @sent) true)))))

    )

  (describe "handler"

    (it "returns an authentication error before websocket upgrade"
      (let [response (sut/handler {:cfg {:gateway {:auth {:mode "token" :token "secret123"}}}}
                                  {:websocket? true :headers {}})]
        (should= 401 (:status response))
        (should= "authentication failed" (:body response))))

    (it "upgrades authenticated websocket requests"
      (let [captured (atom nil)]
        (with-redefs [httpkit/as-channel (fn [request opts]
                                           (reset! captured [request opts])
                                           {:body :channel})]
          (let [response (sut/handler {:cfg {:gateway {:auth {:mode "token" :token "secret123"}}}}
                                      {:websocket? true
                                       :headers    {"authorization" "Bearer secret123"}})]
            (should= :channel (:body response))
            (should-not-be-nil @captured)
            (should (fn? (:on-receive (second @captured))))))))

    (it "logs connection lifecycle events"
      (let [captured (atom nil)]
        (with-redefs [httpkit/as-channel (fn [_request opts]
                                           (reset! captured opts)
                                           :ok)]
          (log/capture-logs
            (sut/handler {:cfg {}}
                         {:websocket? true
                          :uri        "/acp"
                          :headers    {"x-forwarded-for" "127.0.0.1"}})
            ((:on-open @captured) :channel)
            ((:on-close @captured) :channel 1000 "bye")
            (should= [:acp-ws/connection-opened :acp-ws/connection-closed]
                     (mapv :event @log/captured-logs))))))

    (it "logs initialize dispatch"
      (let [captured (atom nil)]
        (with-redefs [httpkit/as-channel           (fn [_request opts]
                                                     (reset! captured opts)
                                                     :ok)
                      httpkit/send!                (fn [_channel _line] nil)
                      isaac.acp.server/dispatch-line (fn [_opts _line]
                                                       {:jsonrpc "2.0"
                                                        :id      1
                                                        :result  {:ok true}})]
          (log/capture-logs
            (sut/handler {:cfg {}}
                         {:websocket? true
                          :uri        "/acp"
                          :headers    {}})
            ((:on-receive @captured) :channel "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}")
            (should= [:acp-ws/initialize]
                     (mapv :event @log/captured-logs))))))

    (it "logs session/new with returned session id"
      (let [captured (atom nil)]
        (with-redefs [httpkit/as-channel           (fn [_request opts]
                                                     (reset! captured opts)
                                                     :ok)
                      httpkit/send!                (fn [_channel _line] nil)
                      isaac.acp.server/dispatch-line (fn [_opts _line]
                                                       {:jsonrpc "2.0" :id 2 :result {:sessionId "agent:main:acp:direct:user1"}})]
          (log/capture-logs
            (sut/handler {:cfg {}}
                         {:websocket? true
                          :uri        "/acp"
                          :headers    {}})
            ((:on-receive @captured) :channel "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"session/new\",\"params\":{}}")
            (should= [{:event :acp-ws/session-new :sessionId "agent:main:acp:direct:user1"}]
                     (mapv #(select-keys % [:event :sessionId]) @log/captured-logs))))))

    (it "applies query params as websocket handler overrides"
      (with-redefs [httpkit/as-channel           (fn [_request opts]
                                                    ((:on-receive opts) :channel "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}")
                                                    :ok)
                    httpkit/send!                (fn [_channel _line] nil)
                    isaac.acp.server/dispatch-line (fn [opts _line]
                                                     (should= "ketch" (:agent-id opts))
                                                     (should= "grover2" (:model-override opts))
                                                     {:jsonrpc "2.0" :id 1 :result {:ok true}})]
        (sut/handler {:cfg {}}
                     {:websocket?  true
                      :uri         "/acp"
                      :query-string "agent=ketch&model=grover2&resume=true"})))

    (it "flushes tool notifications before the final response completes"
      (let [captured (atom nil)
            sent     (atom [])
            release* (promise)]
        (with-redefs [httpkit/as-channel           (fn [_request opts]
                                                     (reset! captured opts)
                                                     :ok)
                      httpkit/send!                (fn [_channel line]
                                                     (swap! sent conj line))
                      isaac.server.acp-websocket/dispatch-line
                      (fn [opts _request _line]
                        ((:output-writer opts) (json/generate-string {:jsonrpc "2.0"
                                                                      :method  "session/update"
                                                                      :params  {:tool "exec"}}))
                        @release*
                        {:jsonrpc "2.0" :id 2 :result {:stopReason "end_turn"}})]
          (sut/handler {:cfg {}}
                       {:websocket? true
                        :uri        "/acp"
                        :headers    {}})
          (future ((:on-receive @captured) :channel "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"session/prompt\",\"params\":{}}"))
          (Thread/sleep 50)
          (should= 1 (count @sent))
          (should= "session/update" (:method (json/parse-string (first @sent) true)))
          (deliver release* :ok)
          (Thread/sleep 50)
          (should= 2 (count @sent)))))

    )

  )
