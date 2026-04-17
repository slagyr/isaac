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

(def top-level-keys
  #{:acp :defaults :dev :gateway :models :providers :server :crew})

(def defaults-keys
  #{:crew :model})

(def crew-keys
  #{:id :model :soul :tools})

(def model-keys
  #{:contextWindow :id :model :provider})

(def provider-keys
  #{:api :apiKey :assistantBaseUrl :baseUrl :headers :id :name :originator :responseFormat :streamSupportsToolCalls :supportsSystemRole :token})
