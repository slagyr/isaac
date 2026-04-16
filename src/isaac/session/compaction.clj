(ns isaac.session.compaction
  (:require [c3kit.apron.schema :as schema]))

(def rubberband-schema
  {:strategy (schema/kind :rubberband)
   :threshold {:kind :int
               :validations [{:validate #(< 0 % 100) :message "must be % between 0 and 100"}]}
   :async? {:kind :boolean
            :default false}})

(def slinky-schema
  {:strategy (schema/kind :slinky)
   :threshold {:kind :int
               :validations [{:validate #(< 0 % 100) :message "must be % between 0 and 100"}]}
   :tail {:kind :int ;; how much of the tail of the transcript gets compacted
          :validations [{:validate #(< 0 % 100) :message "must be % between 0 and 100"}]}
   :async? {:kind :boolean
            :default false}
   :* {:tail {:validations [{:validate #(< (:tail %) (:threshold %)) :message "tail must be smaller than threshold"}]}}})

(def LARGE_TURN_TOKENS 40000)
;; Frontmatter is the content that get sent with every prompt (AGENTS.md, SOUL.md, etc...)
(def LARGE_FRONTMATTER_TOKENS 10000)
(def RECENT_TOPIC_TOKENS 100000) ;; How many tokens hold the current topic of conversation.

(defn default-threshold [window]
  (max (- window (+ LARGE_TURN_TOKENS + LARGE_FRONTMATTER_TOKENS))
       (int (* 0.8 window))))

(defn default-tail [window]
  (max (- window (+ LARGE_TURN_TOKENS + LARGE_FRONTMATTER_TOKENS + RECENT_TOPIC_TOKENS))
       (int (* 0.7 window))))
