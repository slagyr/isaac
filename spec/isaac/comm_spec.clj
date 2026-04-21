(ns isaac.comm-spec
  (:require
    [isaac.comm :as sut]
    [speclj.core :refer :all]))

(describe "Channel protocol"

  (it "can dispatch all channel callbacks"
    (let [events (atom [])
          ch     (reify sut/Comm
                   (on-turn-start [_ session-key input]
                     (swap! events conj [:turn-start session-key input]))
                   (on-text-chunk [_ session-key text]
                     (swap! events conj [:text-chunk session-key text]))
                   (on-tool-call [_ session-key tool-call]
                     (swap! events conj [:tool-call session-key tool-call]))
                   (on-tool-result [_ session-key tool-call result]
                     (swap! events conj [:tool-result session-key tool-call result]))
                   (on-turn-end [_ session-key result]
                     (swap! events conj [:turn-end session-key result]))
                   (on-error [_ session-key error]
                     (swap! events conj [:error session-key error])))]
      (sut/on-turn-start ch "session-1" "hello")
      (sut/on-text-chunk ch "session-1" "chunk")
      (sut/on-tool-call ch "session-1" {:name "read"})
      (sut/on-tool-result ch "session-1" {:name "read"} "ok")
      (sut/on-turn-end ch "session-1" {:content "done"})
      (sut/on-error ch "session-1" {:error :boom})
      (should= 6 (count @events)))))
