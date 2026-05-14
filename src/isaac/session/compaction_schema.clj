(ns isaac.session.compaction-schema)

(def config-schema
  {:strategy  {:type :keyword}
   :threshold {:type        :int
               :validations [{:validate pos?
                              :message  "must be positive"}]}
   :head      {:type        :int
               :validations [{:validate pos?
                              :message  "must be positive"}]}
   :async?    {:type :boolean}
   :*         {:head-threshold {:validate (fn [{:keys [head threshold]}]
                                           (or (nil? head) (nil? threshold) (< head threshold)))
                                :message  "head must be smaller than threshold"}}})
