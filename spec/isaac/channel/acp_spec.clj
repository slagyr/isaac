(ns isaac.channel.acp-spec
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [isaac.channel :as channel]
    [isaac.channel.acp :as sut]
    [speclj.core :refer :all])
  (:import (java.io StringWriter)))

(defn- parsed-output [writer]
  (->> (str/split-lines (str writer))
       (remove str/blank?)
       (mapv #(json/parse-string % true))))

(describe "ACP channel"

  (it "writes text chunk session/update notifications to the output writer"
    (let [writer (StringWriter.)
          ch     (sut/channel writer)]
      (channel/on-text-chunk ch "agent:main:acp:direct:user1" "Once ")
      (channel/on-text-chunk ch "agent:main:acp:direct:user1" " ")
      (channel/on-text-chunk ch "agent:main:acp:direct:user1" "upon ")
      (let [notifications (parsed-output writer)]
        (should= 2 (count notifications))
        (should= "agent_message_chunk" (get-in (first notifications) [:params :update :sessionUpdate]))
        (should= "Once" (get-in (first notifications) [:params :update :content :text]))
        (should= "upon" (get-in (second notifications) [:params :update :content :text])))))

  (it "writes pending and completed tool notifications with sessionId"
    (let [writer    (StringWriter.)
          tool-call {:id "tc-1" :name "exec" :arguments {:command "echo hi"}}
          ch        (sut/channel writer)]
      (channel/on-tool-call ch "agent:main:acp:direct:user1" tool-call)
      (channel/on-tool-result ch "agent:main:acp:direct:user1" tool-call "hi")
      (let [notifications (parsed-output writer)]
        (should= ["tool_call" "tool_call_update"]
                 (mapv #(get-in % [:params :update :sessionUpdate]) notifications))
        (should= "agent:main:acp:direct:user1" (get-in (first notifications) [:params :sessionId]))
        (should= "agent:main:acp:direct:user1" (get-in (second notifications) [:params :sessionId]))
        (should= "pending" (get-in (first notifications) [:params :update :status]))
        (should= "completed" (get-in (second notifications) [:params :update :status]))))

    (it "writes cancelled tool notifications with sessionId"
      (let [writer    (StringWriter.)
            tool-call {:id "tc-1" :name "exec" :arguments {:command "sleep 30"}}
            ch        (sut/channel writer)]
        (channel/on-tool-call ch "agent:main:acp:direct:user1" tool-call)
        (channel/on-tool-cancel ch "agent:main:acp:direct:user1" tool-call)
        (let [notifications (parsed-output writer)]
          (should= ["tool_call" "tool_call_update"]
                   (mapv #(get-in % [:params :update :sessionUpdate]) notifications))
          (should= "pending" (get-in (first notifications) [:params :update :status]))
          (should= "cancelled" (get-in (second notifications) [:params :update :status]))
          (should= "tc-1" (get-in (second notifications) [:params :update :toolCallId])))))

    (it "formats available commands update notifications"
      (let [notification (sut/available-commands-update "cmd-test" [{:name "status"} {:name "model"} {:name "crew"}])]
        (should= "session/update" (:method notification))
        (should= "cmd-test" (get-in notification [:params :sessionId]))
        (should= "available_commands_update" (get-in notification [:params :update :sessionUpdate]))
        (should= ["status" "model" "crew"]
                 (mapv :name (get-in notification [:params :update :availableCommands])))))

    )
  )
