(ns isaac.llm.providers
  "Declarative catalog of built-in LLM provider defaults.

   Each entry is a kebab-case config map used by isaac.llm.api/normalize-pair
   as a baseline before merging user-supplied config.")

(def ^:private catalog
  {"anthropic"      {:api      "anthropic-messages"
                      :base-url "https://api.anthropic.com"
                      :auth     "api-key"
                      :models   ["claude-sonnet-4-6" "claude-opus-4-7" "claude-haiku-4-5"]}
   "claude-sdk"     {:api    "claude-sdk"
                      :auth   "none"
                      :models ["claude-sonnet-4-6" "claude-opus-4-7" "claude-haiku-4-5"]}
   "grover"         {:api    "grover"
                      :auth   "none"
                      :models []}
   "ollama"         {:api      "ollama"
                      :base-url "http://localhost:11434"
                      :auth     "none"
                      :models   []}
   "openai"         {:api      "openai-completions"
                      :base-url "https://api.openai.com/v1"
                      :auth     "api-key"
                      :models   ["gpt-4o" "gpt-4o-mini"]
                      :name     "openai"}
   "openai-api"     {:api      "openai-completions"
                      :base-url "https://api.openai.com/v1"
                      :auth     "api-key"
                      :models   ["gpt-4o" "gpt-4o-mini"]
                      :name     "openai-api"}
   "openai-codex"   {:api      "openai-responses"
                      :base-url "https://api.openai.com/v1"
                      :auth     "oauth-device"
                      :models   ["codex-mini" "gpt-4o"]
                      :name     "openai-chatgpt"}
   "openai-chatgpt" {:api      "openai-responses"
                      :base-url "https://api.openai.com/v1"
                      :auth     "oauth-device"
                      :models   ["gpt-4o" "gpt-4o-mini"]
                      :name     "openai-chatgpt"}
   "grok"           {:api      "openai-completions"
                      :base-url "https://api.x.ai/v1"
                      :auth     "api-key"
                      :models   ["grok-3" "grok-3-mini"]
                      :name     "grok"}})

(defonce ^:private registry* (atom catalog))

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

(defn module-providers [module-index]
  (reduce-kv (fn [providers _module-id module]
               (merge providers
                      (normalize-provider-table
                        (get-in module [:manifest :extends :provider]))))
             {}
             (or module-index {})))

(defn- resolve-provider* [sources provider-name seen]
  (let [provider-name (->id provider-name)]
    (when-not (contains? seen provider-name)
      (let [{:keys [built-ins modules users]} sources
            built-in-entry            (get built-ins provider-name)
            module-entry              (get modules provider-name)
            user-entry                (get users provider-name)
            entry                     (or user-entry module-entry built-in-entry)
            same-name-base            (if user-entry
                                        (merge built-in-entry module-entry)
                                        built-in-entry)
            inherited-provider-name   (->id (:from entry))
            inherited-provider-config (when inherited-provider-name
                                        (resolve-provider* sources inherited-provider-name (conj seen provider-name)))]
        (when entry
          (merge (or inherited-provider-config same-name-base {})
                 (dissoc entry :from)))))))

(defn lookup
  ([provider-name]
   (resolve-provider* {:built-ins @registry* :modules {} :users {}} provider-name #{}))
  ([cfg module-index provider-name]
   (resolve-provider* {:built-ins @registry*
                       :modules   (module-providers module-index)
                       :users     (normalize-provider-table (:providers cfg))}
                      provider-name
                      #{})))

(defn all-providers
  ([]
   (into {}
         (keep (fn [provider-name]
                 (when-let [provider (lookup provider-name)]
                   [provider-name provider])))
         (keys @registry*)))
  ([cfg module-index]
   (let [provider-names (concat (keys @registry*)
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
  (set (keys @registry*)))

(defn grover-defaults
  "Return the config for `provider-name` when it is used as a Grover
   simulation target. Adds :simulate-provider and, for non-oauth providers,
   :api-key grover. Returns nil when the provider is not in the catalog."
  [provider-name]
  (when-let [entry (defaults provider-name)]
    (cond-> (assoc entry :simulate-provider provider-name)
      (not= "oauth-device" (:auth entry)) (assoc :api-key "grover"))))
