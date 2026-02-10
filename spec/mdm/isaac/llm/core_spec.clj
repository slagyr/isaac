(ns mdm.isaac.llm.core-spec
  (:require [mdm.isaac.llm.core :as sut]
            [mdm.isaac.spec-helper :refer [with-config]]
            [speclj.core :refer :all]))

(describe "llm.core"

  (context "chat"

    (it "dispatches based on :llm :impl config (default :ollama)"
      (should= :ollama (sut/chat-impl)))

    (context "with unknown implementation"
      (with-config {:llm {:impl :unknown}})

      (it "throws when implementation not configured"
        (should-throw clojure.lang.ExceptionInfo
                      "Unknown LLM implementation"
                      (sut/chat "test")))))

  (context "chat-with-tools"

    (it "dispatches based on :llm :impl config (default :ollama)"
      (should= :ollama (sut/chat-with-tools-impl)))

    (context "with unknown implementation"
      (with-config {:llm {:impl :unknown}})

      (it "throws when implementation not configured"
        (should-throw clojure.lang.ExceptionInfo
                      "Unknown LLM implementation"
                      (sut/chat-with-tools "test" []))))))
