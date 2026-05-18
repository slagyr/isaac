(ns isaac.session.compaction-schema)

(def config-schema
  {:strategy  {:type :keyword}
   :threshold {:type        :double
               :validations [{:validate #(and (>= % 0.0) (< % 1.0))
                              :message  "must be a percentage in [0.0, 1.0); e.g. 0.8 for 80% of context-window"}]}
   :head      {:type        :double
               :validations [{:validate #(and (>= % 0.0) (< % 1.0))
                              :message  "must be a percentage in [0.0, 1.0); e.g. 0.3 for 30% of context-window"}]}
   :async?    {:type :boolean}
   :*         {:head-threshold {:validate (fn [{:keys [head threshold]}]
                                           (or (nil? head) (nil? threshold) (< head threshold)))
                                :message  "head must be smaller than threshold"}}})
