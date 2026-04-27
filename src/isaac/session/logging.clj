(ns isaac.session.logging
  (:require [isaac.logger :as log]))

(defn log-compaction-check! [key-str provider model total-tokens context-window]
  (log/debug :session/compaction-check
             :session key-str
             :provider provider
             :model model
             :totalTokens total-tokens
             :context-window context-window))

(defn log-compaction-started! [key-str provider model total-tokens context-window]
  (log/info :session/compaction-started
            :session key-str
            :provider provider
            :model model
            :totalTokens total-tokens
            :context-window context-window))

(defn log-message-stored! [key-str model tokens]
  (log/debug :session/message-stored
             :session key-str
             :model model
             :tokens (select-keys tokens [:inputTokens :outputTokens])))

(defn log-stream-completed! [key-str]
  (log/debug :session/stream-completed :session key-str))

(defn log-turn-rejected! [key-str crew reason]
  (log/warn :turn/rejected
            :session key-str
            :crew crew
            :reason reason))

(defn log-turn-accepted! [key-str crew]
  (log/info :turn/accepted
            :session key-str
            :crew crew))

(defn log-crew-changed! [key-str from to]
  (log/info :session/crew-changed
            :session key-str
            :from from
            :to to))
