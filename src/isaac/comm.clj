(ns isaac.comm)

(defprotocol Comm
  (on-turn-start [comm session-key input])
  (on-text-chunk [comm session-key text])
  (on-tool-call [comm session-key tool-call])
  (on-tool-cancel [comm session-key tool-call])
  (on-tool-result [comm session-key tool-call result])
  (on-compaction-start [comm session-key payload])
  (on-compaction-success [comm session-key payload])
  (on-compaction-failure [comm session-key payload])
  (on-compaction-disabled [comm session-key payload])
  (on-turn-end [comm session-key result])
  (on-error [comm session-key error]))
