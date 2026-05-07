(ns isaac.session.compaction
  (:require
    [c3kit.apron.schema :as schema]))

(def LARGE_TURN_TOKENS 40000)
(def LARGE_FRONTMATTER_TOKENS 10000)
(def RECENT_TOPIC_TOKENS 100000)

(def config-schema
  {:strategy  {:type  :one-of
               :specs [{:type :keyword :value :rubberband}
                       {:type :keyword :value :slinky}]}
   :threshold {:type        :int
               :validations [{:validate pos?
                              :message  "must be positive"}]}
   :tail      {:type        :int
               :validations [{:validate pos?
                              :message  "must be positive"}]}
   :async?    {:type :boolean}
   :*         {:tail-threshold {:validate (fn [{:keys [tail threshold]}] (< tail threshold))
                                :message  "tail must be smaller than threshold"}}})

(defn default-threshold [window]
  (max (- window (+ LARGE_TURN_TOKENS LARGE_FRONTMATTER_TOKENS))
       (int (* 0.8 window))))

(defn default-tail [window]
  (max (- window (+ LARGE_TURN_TOKENS LARGE_FRONTMATTER_TOKENS RECENT_TOPIC_TOKENS))
       (int (* 0.7 window))))

(defn resolve-config [session-entry context-window]
  (let [defaults {:async?    false
                   :strategy  :rubberband
                   :tail      (default-tail context-window)
                   :threshold (default-threshold context-window)}
        raw      (merge defaults (select-keys (:compaction session-entry) [:async? :strategy :tail :threshold]))]
    (schema/coerce! config-schema raw)))

(defn should-compact? [session-entry context-window]
  (let [total (:last-input-tokens session-entry 0)
        {:keys [threshold]} (resolve-config session-entry context-window)]
    (>= total threshold)))

(defn compaction-target [entries {:keys [strategy tail]}]
  (let [tokens* (mapv :tokens entries)]
    (case strategy
      :rubberband
      {:compact-count        (count entries)
       :first-kept-entry-id  nil
       :tokens-before        (reduce + 0 tokens*)}

      :slinky
      (loop [idx       (dec (count entries))
             preserved 0]
        (if (or (neg? idx) (>= preserved tail))
          (let [compact-count (inc idx)
                compacted     (subvec entries 0 (max 0 compact-count))
                first-kept    (nth entries compact-count nil)]
            {:compact-count       (max 0 compact-count)
             :first-kept-entry-id (:id first-kept)
             :tokens-before       (reduce + 0 (map :tokens compacted))})
          (recur (dec idx) (+ preserved (:tokens (nth entries idx)))))))))
