(ns isaac.session.logging
  (:require [isaac.logger :as log]))

(defn log-compaction-check! [key-str provider model total-tokens context-window]
  (log/debug :session/compaction-check
             :session key-str
             :provider provider
             :model model
             :totalTokens total-tokens
             :contextWindow context-window))

(defn log-compaction-started! [key-str provider model total-tokens context-window]
  (log/info :session/compaction-started
            :session key-str
            :provider provider
            :model model
            :totalTokens total-tokens
            :contextWindow context-window))

(defn log-message-stored! [key-str model tokens]
  (log/debug :session/message-stored
             :session key-str
             :model model
             :tokens (select-keys tokens [:inputTokens :outputTokens])))

(defn log-stream-completed! [key-str]
  (log/debug :session/stream-completed :session key-str))
