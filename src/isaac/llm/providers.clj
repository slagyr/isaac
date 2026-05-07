(ns isaac.llm.providers
  "Declarative catalog of built-in LLM provider defaults.

   Each entry is a kebab-case config map used by isaac.llm.api/normalize-pair
   as a baseline before merging user-supplied config. Keys present in catalog
   entries:

     :api        — wire-spec id string (e.g. \"openai-completions\")
     :base-url   — provider endpoint root
     :auth       — auth mode: \"oauth-device\" | nil (api-key or no auth)
     :name       — canonical provider name when the public alias differs
                   (e.g. openai-codex → openai-chatgpt)")

(def ^:private catalog
  {"anthropic"      {:api "anthropic-messages"}
   "claude-sdk"     {:api "claude-sdk"}
   "grover"         {:api "grover"}
   "ollama"         {:api "ollama"             :base-url "http://localhost:11434"}
   "openai"         {:api "openai-completions" :base-url "https://api.openai.com/v1" :name "openai"}
   "openai-api"     {:api "openai-completions" :base-url "https://api.openai.com/v1" :name "openai-api"}
   "openai-codex"   {:api "openai-responses"   :base-url "https://api.openai.com/v1" :name "openai-chatgpt" :auth "oauth-device"}
   "openai-chatgpt" {:api "openai-responses"   :base-url "https://api.openai.com/v1" :name "openai-chatgpt" :auth "oauth-device"}
   "grok"           {:api "openai-completions" :base-url "https://api.x.ai/v1"       :name "grok"}})

(defn defaults
  "Return the default config map for `provider-name`, or nil if unknown."
  [provider-name]
  (get catalog provider-name))

(defn known-providers
  "Return the set of built-in provider names in the catalog."
  []
  (set (keys catalog)))

(defn grover-defaults
  "Return the config for `provider-name` when it is used as a Grover
   simulation target. Adds :simulate-provider and, for non-oauth providers,
   :api-key \"grover\". Returns nil when the provider is not in the catalog."
  [provider-name]
  (when-let [entry (get catalog provider-name)]
    (cond-> (assoc entry :simulate-provider provider-name)
      (not= "oauth-device" (:auth entry)) (assoc :api-key "grover"))))
