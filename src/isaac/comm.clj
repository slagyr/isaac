(ns isaac.comm)

(defprotocol Comm
  (on-turn-start [ch session-key input])
  (on-text-chunk [ch session-key text])
  (on-thought-chunk [ch session-key text])
  (on-tool-call [ch session-key tool-call])
  (on-tool-cancel [ch session-key tool-call])
  (on-tool-result [ch session-key tool-call result])
  (on-turn-end [ch session-key result])
  (on-error [ch session-key error]))
