(ns isaac.acp.ws-reconnect-spec
  (:require
    [isaac.acp.ws :as ws]
    [speclj.core :refer :all]))

(describe "Reconnectable loopback transport"

  (it "connects a client to the current server instance"
    (let [transport (ws/reconnectable-loopback)
          client    (ws/connect-loopback! transport "ws://test/acp")
          server    (ws/accept-loopback! transport)]
      (ws/ws-send! client "hello")
      (should= "hello" (ws/ws-receive! server 50))
      (ws/ws-close! client)
      (ws/ws-close! server)))

  (it "returns a fresh connection after restore"
    (let [transport (ws/reconnectable-loopback)
          client-1  (ws/connect-loopback! transport "ws://test/acp")
          server-1  (ws/accept-loopback! transport)]
      (ws/drop-loopback! transport)
      (should= nil (ws/ws-receive! client-1 50))
      (ws/restore-loopback! transport)
      (let [client-2 (ws/connect-loopback! transport "ws://test/acp")
            server-2 (ws/accept-loopback! transport)]
        (ws/ws-send! client-2 "again")
        (should= "again" (ws/ws-receive! server-2 50))
        (ws/ws-close! client-1)
        (ws/ws-close! server-1)
        (ws/ws-close! client-2)
        (ws/ws-close! server-2))))

  (it "refuses new connections after permanent drop"
    (let [transport (ws/reconnectable-loopback)]
      (ws/drop-loopback-permanently! transport)
      (should-throw clojure.lang.ExceptionInfo (ws/connect-loopback! transport "ws://test/acp")))))
