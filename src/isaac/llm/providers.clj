(ns isaac.llm.providers
  "Declarative catalog of built-in LLM provider defaults.

   Each entry is a kebab-case config map used by isaac.llm.api/normalize-pair
   as a baseline before merging user-supplied config.")

(defn- ->id [value]
  (cond
    (keyword? value) (name value)
    (string? value)  value
    (nil? value)     nil
    :else            (str value)))

(defn- normalize-provider-table [providers]
  (into {}
        (map (fn [[id provider]] [(->id id) provider]))
        (or providers {})))

(defn- normalize-manifest-providers [providers]
  (into {}
        (map (fn [[id entry]]
               [(->id id) (or (:template entry) entry)]))
        (or providers {})))

(defn- core-catalog
  "Provider templates declared in the core manifest. Lazy-resolves
   `isaac.module.loader/core-index` to avoid a load-time cycle (module.loader
   transitively requires this namespace via isaac.llm.api)."
  []
  (let [core-index-fn (requiring-resolve 'isaac.module.loader/core-index)
        core-entry    (get (core-index-fn) :isaac.core)]
    (normalize-manifest-providers (get-in core-entry [:manifest :provider]))))

;; Overlay for dynamically-registered providers (tests / future plugins).
;; Manifest providers come from core-catalog above.
(defonce ^:private registry* (atom {}))

(defn- effective-registry []
  (merge (core-catalog) @registry*))

(def ^:private core-module-id :isaac.core)

(defn module-providers
  "Provider entries declared by third-party modules. The core manifest's
   provider entries are templates accessible via `template`; they do not
   materialize through this path."
  [module-index]
  (reduce-kv (fn [providers module-id module]
               (if (= core-module-id module-id)
                 providers
                 (merge providers
                        (normalize-manifest-providers
                          (get-in module [:manifest :provider])))))
             {}
             (or module-index {})))

(defn- resolve-provider* [sources provider-name seen require-instance?]
  (let [provider-name (->id provider-name)]
    (when-not (contains? seen provider-name)
      (let [{:keys [built-ins modules users]} sources
            built-in-entry            (get built-ins provider-name)
            module-entry              (get modules provider-name)
            user-entry                (get users provider-name)
            entry                     (if require-instance?
                                        (or user-entry module-entry)
                                        (or user-entry module-entry built-in-entry))
            same-name-base            (if user-entry
                                        (merge built-in-entry module-entry)
                                        built-in-entry)
            inherited-provider-name   (->id (or (:type entry) (:from entry)))
            inherited-provider-config (when inherited-provider-name
                                        (resolve-provider* sources inherited-provider-name (conj seen provider-name) false))]
        (when entry
          (merge (or inherited-provider-config same-name-base {})
                 (dissoc entry :type :from)))))))

(defn template
  "Return the provider template for `provider-name`, or nil if unknown."
  ([provider-name]
   (resolve-provider* {:built-ins (effective-registry) :modules {} :users {}} provider-name #{} false))
  ([cfg module-index provider-name]
   (resolve-provider* {:built-ins (effective-registry)
                       :modules   (module-providers module-index)
                       :users     (normalize-provider-table (:providers cfg))}
                      provider-name
                      #{}
                      false)))

(defn lookup
  ([provider-name]
   (resolve-provider* {:built-ins (effective-registry) :modules {} :users {}} provider-name #{} true))
  ([cfg module-index provider-name]
   (resolve-provider* {:built-ins (effective-registry)
                       :modules   (module-providers module-index)
                       :users     (normalize-provider-table (:providers cfg))}
                      provider-name
                      #{}
                      true)))

(defn all-providers
  ([]
   (into {}
         (keep (fn [provider-name]
                 (when-let [provider (lookup provider-name)]
                   [provider-name provider])))
         (keys (effective-registry))))
  ([cfg module-index]
   (let [provider-names (concat (keys (effective-registry))
                                (keys (module-providers module-index))
                                (keys (normalize-provider-table (:providers cfg))))]
     (into {}
           (keep (fn [provider-name]
                   (when-let [provider (lookup cfg module-index provider-name)]
                     [provider-name provider])))
           (distinct provider-names)))))

(defn register!
  "Register a provider config under `provider-name`. Returns the normalized name."
  [provider-name provider-config]
  (let [provider-name (->id provider-name)]
    (swap! registry* assoc provider-name provider-config)
    provider-name))

(defn unregister!
  "Remove the provider config registered for `provider-name`. Returns the normalized name."
  [provider-name]
  (let [provider-name (->id provider-name)]
    (swap! registry* dissoc provider-name)
    provider-name))

(defn defaults
  "Return the default config map for `provider-name`, or nil if unknown."
  [provider-name]
  (lookup provider-name))

(defn known-providers
  "Return the set of built-in provider names in the catalog."
  []
  (set (keys (effective-registry))))

(defn grover-defaults
  "Return the config for `provider-name` when it is used as a Grover
   simulation target. Adds :simulate-provider and, for non-oauth providers,
   :api-key grover. Returns nil when the provider is not in the catalog."
  [provider-name]
  (when-let [entry (template provider-name)]
    (cond-> (assoc entry :simulate-provider provider-name)
      (not= "oauth-device" (:auth entry)) (assoc :api-key "grover"))))
