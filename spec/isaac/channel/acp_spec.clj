(ns isaac.channel.acp-spec
  (:require
    [isaac.channel :as channel]
    [isaac.channel.acp :as sut]
    [speclj.core :refer :all]))

(describe "ACP channel"

  (it "buffers text chunk session/update notifications"
    (let [notifications (atom [])
          ch            (sut/channel notifications)]
      (channel/on-text-chunk ch "agent:main:acp:direct:user1" "Once ")
      (channel/on-text-chunk ch "agent:main:acp:direct:user1" " ")
      (channel/on-text-chunk ch "agent:main:acp:direct:user1" "upon ")
      (should= 2 (count @notifications))
      (should= "agent_message_chunk" (get-in (first @notifications) [:params :update :sessionUpdate]))
      (should= "Once" (get-in (first @notifications) [:params :update :content :text]))
      (should= "upon" (get-in (second @notifications) [:params :update :content :text]))))

  (it "buffers pending and completed tool notifications"
    (let [notifications (atom [])
          tool-call     {:id "tc-1" :name "exec" :arguments {:command "echo hi"}}
          ch            (sut/channel notifications)]
      (channel/on-tool-call ch "agent:main:acp:direct:user1" tool-call)
      (channel/on-tool-result ch "agent:main:acp:direct:user1" tool-call "hi")
      (should= ["tool_call" "tool_call_update"]
               (mapv #(get-in % [:params :update :sessionUpdate]) @notifications))
      (should= "pending" (get-in (first @notifications) [:params :update :status]))
      (should= "completed" (get-in (second @notifications) [:params :update :status])))))
