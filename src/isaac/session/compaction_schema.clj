(ns isaac.session.compaction-schema)

(def config-schema
  {:strategy  {:type :keyword}
   :threshold {:type        :double
               :validations [{:validate #(>= % 0.0)
                              :message  "must be a non-negative token count"}]}
   :head      {:type        :double
               :validations [{:validate #(>= % 0.0)
                              :message  "must be a non-negative token count"}]}
   :async?    {:type :boolean}
   :*         {:head-threshold {:validate (fn [{:keys [head threshold]}]
                                           (or (nil? head) (nil? threshold) (< head threshold)))
                                :message  "head must be smaller than threshold"}}})
