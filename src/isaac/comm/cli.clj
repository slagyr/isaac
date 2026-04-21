(ns isaac.comm.cli
  (:require
    [isaac.comm :as comm]))

(deftype CliComm []
  comm/Comm
  (on-turn-start [_ _ _] nil)
  (on-text-chunk [_ _ text]
    (print text)
    (flush))
  (on-tool-call [_ _ tool-call]
    (println (str "  [tool call: " (:name tool-call) "]")))
  (on-tool-cancel [_ _ _] nil)
  (on-tool-result [_ _ _ _] nil)
  (on-turn-end [_ _ _]
    (println))
  (on-error [_ _ _] nil))

(def channel (->CliComm))
