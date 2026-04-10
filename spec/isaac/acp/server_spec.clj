(ns isaac.acp.server-spec
  (:require
    [clojure.java.io :as io]
    [isaac.acp.server :as sut]
    [isaac.session.storage :as storage]
    [speclj.core :refer :all]))

(def test-dir "target/test-acp-server")

(defn- clean-dir! [path]
  (let [dir (io/file path)]
    (when (.exists dir)
      (doseq [f (reverse (file-seq dir))]
        (.delete f)))))

(describe "ACP server"

  (before (clean-dir! test-dir))

  (describe "initialize"

    (it "returns protocol version, agent info, and capabilities"
      (let [response (sut/dispatch-line {:state-dir test-dir}
                                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":1}}")]
        (should= 1 (get-in response [:result :protocolVersion]))
        (should= "isaac" (get-in response [:result :agentInfo :name]))
        (should= true (get-in response [:result :agentCapabilities :loadSession]))
        (should= true (get-in response [:result :agentCapabilities :promptCapabilities :text])))))

  (describe "session/new"

    (it "creates an ACP channel session for main agent"
      (let [response   (sut/dispatch-line {:state-dir test-dir}
                                          "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"session/new\",\"params\":{\"cwd\":\"/tmp/project\"}}")
            session-id (get-in response [:result :sessionId])
            sessions   (storage/list-sessions test-dir "main")]
        (should (re-matches #"agent:main:acp:direct:.+" session-id))
        (should= 1 (count sessions))
        (should= session-id (:key (first sessions))))))

  )
