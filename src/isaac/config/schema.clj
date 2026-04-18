(ns isaac.config.schema)

(def default-config
  {:defaults  {:crew "main"
               :model "llama"}
   :crew      {"main" {}}
   :models    {"llama" {:model         "llama3.3:1b"
                         :provider      "ollama"
                         :contextWindow 32768}}
   :providers {"ollama" {:api     "ollama"
                          :baseUrl "http://localhost:11434"}}})

(def defaults-schema
  {:crew  {:type :string :doc "Default crew member id"}
   :model {:type :string :doc "Default model alias"}})

(def crew-schema
  {:id    {:type :string :doc "Crew member id; must match filename"}
   :model {:type :string :doc "Model alias"}
   :soul  {:type :string :doc "System prompt"}
   :tools {:type :map    :doc "Tool configuration"}})

(def model-schema
  {:id            {:type :string :doc "Model alias; must match filename"}
   :model         {:type :string :doc "Provider-specific model name or id"}
   :provider      {:type :string :doc "Provider alias"}
   :contextWindow {:type :int    :doc "Context window size in tokens"}})

(def provider-schema
  {:api                     {:type :string  :doc "Provider API adapter (e.g. \"anthropic\", \"ollama\")"}
   :apiKey                  {:type :string  :doc "API key"}
   :assistantBaseUrl        {:type :string  :doc "Base URL for assistant endpoints"}
   :baseUrl                 {:type :string  :doc "API base URL"}
   :headers                 {:type :map     :doc "Extra HTTP headers to include in requests"}
   :id                      {:type :string  :doc "Provider id; must match filename"}
   :name                    {:type :string  :doc "Display name"}
   :originator              {:type :string  :doc "X-Originator header value"}
   :responseFormat          {:type :string  :doc "Response format hint"}
   :streamSupportsToolCalls {:type :boolean :doc "Whether streaming mode supports tool calls"}
   :supportsSystemRole      {:type :boolean :doc "Whether the provider accepts a system role message"}
   :token                   {:type :string  :doc "Authentication token (alias for apiKey)"}})

(def root-schema
  {:acp       {:type :map :doc "Agent Communication Protocol configuration"}
   :crew      {:type :map :doc "Crew member configurations (map of id → crew entity)"}
   :defaults  {:type :map :doc "Default crew and model selections"}
   :dev       {:type :any :doc "Development mode flag"}
   :gateway   {:type :map :doc "Gateway server configuration"}
   :models    {:type :map :doc "Model configurations (map of id → model entity)"}
   :providers {:type :map :doc "Provider configurations (map of id → provider entity)"}
   :server    {:type :map :doc "HTTP server configuration"}})

(def top-level-keys (set (keys root-schema)))
(def defaults-keys (set (keys defaults-schema)))
(def crew-keys (set (keys crew-schema)))
(def model-keys (set (keys model-schema)))
(def provider-keys (set (keys provider-schema)))
