(ns isaac.server.acp-websocket-spec
  (:require
    [cheshire.core :as json]
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

  ))
