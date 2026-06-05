(ns isaac.schema.registered-in
  "`:registered-in?` validation primitive — asserts a value is the id of a
   contribution registered to a named berth. Wired into apron's
   validations lexicon at load time so config schemas can write

     :type {:type        :keyword
            :validations [:present? [:registered-in? :isaac.server/comm]]}

   and have the validator pull the live contribution set from the
   ambient module-index. Callers bind `*module-index*` before running
   validation (the contribution-validation pass in
   `isaac.module.loader` does this); for direct use (specs,
   embedders) bind it yourself."
  (:require
    [c3kit.apron.schema :as schema]))

(def ^{:dynamic true
       :doc "Module-index the `:registered-in?` validator should resolve
            berth contributions against. nil ⇒ treated as empty."}
  *module-index* nil)

;; Cap on listing accepted ids in the failure message — keep small lists
;; informative without flooding the error when a berth has many
;; contributions. Picked low; tune later if needed.
(def ^:private accepted-ids-list-cap 5)

(defn- berth-declared? [module-index berth-id]
  (some (fn [[_ entry]]
          (get-in entry [:manifest :berths berth-id]))
        module-index))

(defn- contributions-for-berth [module-index berth-id]
  (->> module-index
       (mapcat (fn [[_ entry]]
                 (when-let [v (get-in entry [:manifest berth-id])]
                   (when (map? v) (keys v)))))
       (into #{})))

(defn- fail!
  "Apron resolves a validation failure's message in this order:
   ex-data :message → ex-data :message → ex-message → lex default.
   Throwing with the message in ex-data lets the validator pick the
   message dynamically based on what it actually found."
  [msg]
  (throw (ex-info msg {:message msg})))

(defn registered-in?
  "Validation factory: pass when `value` is a contribution id registered
   to `berth-id` across `*module-index*`. Distinct failure messages for
   unknown berth, empty contribution set, and bad value.

   Returns a validation map with a `:known` thunk so the CLI renderer
   (isaac.config.cli.validate) can list the accepted ids alongside the
   failure (mirrors how exists-ref-based validators surface the valid
   set)."
  [berth-id]
  {:validate (fn [value]
               (let [mi (or *module-index* {})]
                 (cond
                   (not (berth-declared? mi berth-id))
                   (fail! (str "unknown berth: " berth-id))

                   :else
                   (let [accepted (contributions-for-berth mi berth-id)]
                     (cond
                       (empty? accepted)
                       (fail! (str "no registered impls for berth " berth-id))

                       (contains? accepted value)
                       true

                       (<= (count accepted) accepted-ids-list-cap)
                       (fail! (str "must be one of " (vec (sort accepted))))

                       :else
                       (fail! (str "must be a registered contribution to " berth-id)))))))
   :message  (str "must be a registered contribution to " berth-id)
   :known    (fn []
               (let [mi (or *module-index* {})]
                 (->> (contributions-for-berth mi berth-id)
                      (map name)
                      sort
                      vec)))})

;; Wire into apron's validation lexicon so any schema can reference the
;; factory by name: `:validations [[:registered-in? :foo/berth]]`.
(schema/update-lexicon! :validations assoc :registered-in? registered-in?)
