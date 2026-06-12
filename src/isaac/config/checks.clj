(ns isaac.config.checks
  (:require
    [c3kit.apron.schema :as cs]
    [isaac.config.berths :as berths]
    [isaac.config.schema-base :as schema-base]
    [isaac.config.schema-compose :as schema-compose]
    [isaac.config.validation :as validation]
    [isaac.module.loader :as module-loader]))

(defn- ->id [value]
  (schema-base/->id value))

(defn- find-manifest-entry [config section name]
  (let [kw (keyword (->id name))]
    (some (fn [[_ entry]]
            (get-in entry [:manifest section kw]))
          (merge (module-loader/builtin-index) (:module-index config)))))

(defn- find-tool-manifest-entry [config tool-name]
  (find-manifest-entry config :isaac.server/tools tool-name))

(defn- find-slash-command-manifest-entry [config command-name]
  (find-manifest-entry config :isaac.server/slash-commands command-name))

(def ^:private manifest-schema-kinds
  [:isaac.server/comm :isaac.server/provider-template :isaac.server/slash-commands :isaac.server/tools])

(defn- verify-manifest-schema-fragment [module-id field-schema]
  (try
    (cs/verify-schema-lexes field-schema)
    []
    (catch Throwable t
      [{:key   (str "modules." (->id module-id))
        :value (if-let [ref (or (:ref (ex-data t))
                                (:lex (ex-data t)))]
                 (str "unregistered ref " ref)
                 (.getMessage t))}])))

(defn- manifest-ref-errors [module-index]
  (mapcat (fn [[module-id entry]]
            (mapcat (fn [kind]
                      (mapcat (fn [[_ extension]]
                                (when-let [field-schema (:schema extension)]
                                  (verify-manifest-schema-fragment module-id field-schema)))
                              (get-in entry [:manifest kind])))
                    manifest-schema-kinds))
          module-index))

(defn- comm-reserved-schema-errors [module-index]
  (mapcat (fn [[module-id entry]]
            (keep (fn [[extension-id extension]]
                    (when (contains? (:schema extension) :type)
                      {:key   (str "modules." (->id module-id))
                       :value (str ":type is the slot discriminator, not a field"
                                   " (comm " (name extension-id) ")")}))
                  (get-in entry [:manifest :isaac.server/comm])))
          module-index))

(defn- one-of-values [field-spec]
  (some (fn [validation]
          (when (and (vector? validation)
                     (contains? #{:one-of :one-of?} (first validation)))
            (into #{} (map ->id) (rest validation))))
        (:validations field-spec)))

(defn- tool-provider-warning [prefix tool-cfg field-schema result]
  (let [provider-key   (str prefix ".provider")
        provider-spec  (:provider field-schema)
        known-values   (one-of-values provider-spec)
        provider-value (some-> (:provider tool-cfg) ->id)]
    (if (and (seq known-values)
             provider-value
             (not (contains? known-values provider-value)))
      {:errors   (remove #(= provider-key (:key %)) (:errors result))
       :warnings (conj (vec (:warnings result)) {:key provider-key :value "unknown provider"})}
      result)))

(defn check-tools
  [{:keys [config]}]
  (let [tools-config (:tools config)]
    (if (empty? tools-config)
      {:errors [] :warnings []}
      (binding [validation/*config* (validation/validation-context config)]
        (reduce
          (fn [{:keys [errors warnings]} [tool-kw tool-cfg]]
            (let [tool-name   (name tool-kw)
                  tool-fields (:schema (find-tool-manifest-entry config tool-name))]
              (if (nil? tool-fields)
                {:errors errors :warnings warnings}
                (let [prefix (str "tools." tool-name)
                      check  (->> (validation/validate-manifest-config prefix tool-cfg tool-fields)
                                  (tool-provider-warning prefix tool-cfg tool-fields))]
                  {:errors   (into errors (:errors check))
                   :warnings (into warnings (:warnings check))}))))
          {:errors [] :warnings []}
          tools-config)))))

(defn check-slash-commands
  [{:keys [config]}]
  (let [commands-config (:slash-commands config)]
    (if (empty? commands-config)
      {:errors [] :warnings []}
      (binding [validation/*config* (validation/validation-context config)]
        (reduce (fn [{:keys [errors warnings]} [command-kw command-cfg]]
                  (let [command-name (name command-kw)
                        known-fields (:schema (find-slash-command-manifest-entry config command-name))]
                    (if (nil? known-fields)
                      {:errors errors :warnings warnings}
                      (let [prefix (str "slash-commands." command-name)
                            check  (validation/validate-manifest-config prefix command-cfg known-fields)]
                        {:errors   (into errors (:errors check))
                         :warnings (into warnings (:warnings check))}))))
                {:errors [] :warnings []}
                commands-config)))))

(defn- impl->kw [impl-val]
  (cond
    (keyword? impl-val) impl-val
    (string? impl-val) (keyword impl-val)
    :else nil))

(defn- find-comm-extension [module-index impl-kw]
  (some (fn [[_id entry]]
          (get-in entry [:manifest :isaac.server/comm impl-kw]))
        module-index))

(defn check-comms
  [{:keys [config module-index effective-schema]}]
  (if (berths/claims-path? module-index [:comms])
    {:errors [] :warnings []}
    (let [comms (:comms config)]
      (if (empty? comms)
        {:errors [] :warnings []}
        (binding [validation/*config* (validation/validation-context config)]
          (reduce (fn [{:keys [errors warnings]} [slot-id slot-cfg]]
                    (let [type-kw (or (impl->kw (:type slot-cfg))
                                      (impl->kw slot-id))]
                      (if (nil? type-kw)
                        {:errors errors :warnings warnings}
                        (let [entry       (find-comm-extension module-index type-kw)
                              schema-flds (or (:schema entry) {})
                              prefix      (str "comms." (name slot-id))
                              result      (validation/validate-manifest-config prefix slot-cfg schema-flds
                                                                             :ignore-keys (schema-compose/comm-base-fields effective-schema))]
                          {:errors   (into errors (:errors result))
                           :warnings (into warnings (:warnings result))}))))
                  {:errors [] :warnings []}
                  comms))))))

(defn- find-provider-manifest-entry [module-index type-name]
  (let [type-kw (keyword (->id type-name))]
    (some (fn [[_id entry]]
            (get-in entry [:manifest :isaac.server/provider-template type-kw]))
          module-index)))

(defn check-provider-types
  [{:keys [config raw-providers module-index]}]
  (if (empty? raw-providers)
    {:errors [] :warnings []}
    (binding [validation/*config* (validation/validation-context config)]
      (reduce (fn [{:keys [errors warnings]} [provider-id provider-cfg]]
                (let [type-name (->id (or (:type provider-cfg) (:from provider-cfg)))]
                  (if (nil? type-name)
                    {:errors errors :warnings warnings}
                    (let [entry  (find-provider-manifest-entry
                                   (merge (module-loader/builtin-index) module-index) type-name)
                          schema (:schema entry)
                          prefix (str "providers." (->id provider-id))]
                      (if (nil? schema)
                        {:errors errors :warnings warnings}
                        (let [result (validation/validate-manifest-config prefix provider-cfg schema
                                                                            :ignore-keys #{:from :type}
                                                                            :warn-unknown? false)]
                          {:errors   (into errors (:errors result))
                           :warnings (into warnings (:warnings result))}))))))
              {:errors [] :warnings []}
              raw-providers))))

(defn check-resolved-providers
  [{:keys [config raw-providers effective-schema]}]
  (let [resolve-provider (requiring-resolve 'isaac.config.resolve/resolve-provider)
        provider-schema  (schema-compose/provider-entity-schema effective-schema)]
    {:errors (vec
               (mapcat (fn [[provider-id provider-cfg]]
                         (when (or (:type provider-cfg) (:from provider-cfg))
                           (when-let [resolved (resolve-provider config provider-id)]
                             (validation/annotation-errors* nil ["providers" (->id provider-id)] provider-schema resolved resolved nil))))
                       raw-providers))
     :warnings []}))

(defn check-manifest-refs
  [{:keys [module-index]}]
  {:errors (vec (manifest-ref-errors module-index))
   :warnings []})

(defn check-comm-reserved-schema
  [{:keys [module-index]}]
  {:errors (vec (comm-reserved-schema-errors module-index))
   :warnings []})