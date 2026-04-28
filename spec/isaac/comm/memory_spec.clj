(ns isaac.comm.memory-spec
  (:require
    [isaac.comm :as comm]
    [isaac.comm.memory :as sut]
    [speclj.core :refer :all]))

(describe "Memory channel"

  (it "records text events in order"
    (let [events (atom [])
          ch     (sut/channel events)]
      (comm/on-turn-start ch "agent:main:cli:direct:user1" "What is 2+2?")
      (comm/on-text-chunk ch "agent:main:cli:direct:user1" "Four, I think")
      (comm/on-turn-end ch "agent:main:cli:direct:user1" {:content "Four, I think"})
      (should= ["turn-start" "text-chunk" "turn-end"] (mapv :event @events))))

  (it "records thought events separately from text events"
    (let [events (atom [])
          ch     (sut/channel events)]
      (comm/on-thought-chunk ch "agent:main:cli:direct:user1" "compacting...")
      (should= [{:event "thought-chunk" :session "agent:main:cli:direct:user1" :text "compacting..."}]
               @events)))

  (it "records tool lifecycle events"
    (let [events    (atom [])
          tool-call {:id "tc-1" :name "exec" :arguments {:command "echo hi"}}
          ch        (sut/channel events)]
      (comm/on-tool-call ch "agent:main:cli:direct:user1" tool-call)
      (comm/on-tool-result ch "agent:main:cli:direct:user1" tool-call "hi")
      (should= "tool-call" (:event (first @events)))
      (should= "exec" (get-in (first @events) [:tool :name]))
      (should= "tool-result" (:event (second @events)))
      (should= "exec" (get-in (second @events) [:tool :name])))))
