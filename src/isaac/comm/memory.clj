(ns isaac.comm.memory
  (:require
    [clojure.string :as str]
    [isaac.comm :as comm]))

(defn- append! [events event]
  (swap! events conj event))

(deftype MemoryComm [events]
  comm/Comm
  (on-turn-start [_ session-key input]
    (append! events {:event "turn-start" :session session-key :input input}))
  (on-text-chunk [_ session-key text]
    (let [trimmed (some-> text str/trim)]
      (when (seq trimmed)
        (append! events {:event "text-chunk" :session session-key :text trimmed}))))
  (on-tool-call [_ session-key tool-call]
    (append! events {:event "tool-call" :session session-key :tool {:name (:name tool-call)}}))
  (on-tool-result [_ session-key tool-call result]
    (append! events {:event "tool-result" :session session-key :tool {:name (:name tool-call)} :result result}))
  (on-turn-end [_ session-key result]
    (append! events {:event "turn-end" :session session-key :result result}))
  (on-error [_ session-key error]
    (append! events {:event "error" :session session-key :error error})))

(defn channel [events]
  (->MemoryComm events))
