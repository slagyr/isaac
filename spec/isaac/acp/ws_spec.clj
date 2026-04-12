(ns isaac.acp.ws-spec
  (:require
    [isaac.acp.ws :as sut]
    [speclj.core :refer :all]))

(describe "ACP WebSocket transport"

  (describe "loopback-pair"

    (it "delivers messages from client to server"
      (let [{:keys [client server]} (sut/loopback-pair)]
        (sut/ws-send! client "{\"id\":1}")
        (should= "{\"id\":1}" (sut/ws-receive! server 50))
        (sut/ws-close! client)
        (sut/ws-close! server)))

    (it "returns nil after close when no more messages are available"
      (let [{:keys [client server]} (sut/loopback-pair)]
        (sut/ws-close! client)
        (sut/ws-close! server)
        (should= nil (sut/ws-receive! server 10)))))

  (describe "written-lines"

    (it "returns complete non-blank lines from a writer"
      (let [writer (java.io.StringWriter.)]
        (.write writer "{\"id\":1}\n\n{\"id\":2}\n")
        (should= ["{\"id\":1}" "{\"id\":2}"] (sut/written-lines writer)))))

  )
