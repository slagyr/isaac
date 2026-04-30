(ns isaac.provider
  "Protocol for an LLM provider — the gateway to a thinking-engine
   (Anthropic, OpenAI, Ollama, Claude SDK, Grover test stub).

   Implementations live alongside their wire code in isaac.llm.<name>.
   The `make` factory in isaac.drive.dispatch resolves a (name, config)
   pair to a Provider instance — it lives there to avoid a cycle, since
   the impl namespaces all require this one for the protocol."
  (:require
    [clojure.string :as str]))

(defprotocol Provider
  (chat
    [this request]
    "One-shot LLM call. Returns a normalized response map with
     :message, :model, :usage, optional :tool-calls.")

  (chat-stream
    [this request on-chunk]
    "Streaming LLM call. Calls on-chunk per text delta as it arrives.
     Returns the final accumulated response (same shape as chat).")

  (followup-messages
    [this request response tool-calls tool-results]
    "Build the next iteration's :messages vector for the tool loop in
     this provider's wire format. Used by isaac.llm.tool-loop/run
     between chat iterations.")

  (config
    [this]
    "Return this provider's raw (kebab-case) config map. Used for
     introspection — e.g. `:stream-supports-tool-calls`. The wire
     format used for outbound calls is kept inside the deftype.")

  (display-name
    [this]
    "Original provider name string — `anthropic`, `openai-codex`,
     `grover:openai`, etc. Used for log lines and observability."))

;; --- Construction ---

(defn- simulated-provider-target [provider]
  (when (str/starts-with? provider "grover:")
    (subs provider (count "grover:"))))

(defn- simulated-provider-config [provider]
  (case provider
    "openai"         {:api "openai-compatible" :api-key "grover" :base-url "https://api.openai.com/v1"
                      :name "openai" :simulate-provider "openai"}
    "openai-api"     {:api "openai-compatible" :api-key "grover" :base-url "https://api.openai.com/v1"
                      :name "openai-api" :simulate-provider "openai-api"}
    "openai-codex"   {:api "openai-compatible" :auth "oauth-device" :base-url "https://api.openai.com/v1"
                      :name "openai-chatgpt" :simulate-provider "openai-chatgpt"}
    "openai-chatgpt" {:api "openai-compatible" :auth "oauth-device" :base-url "https://api.openai.com/v1"
                      :name "openai-chatgpt" :simulate-provider "openai-chatgpt"}
    "grok"           {:api "openai-compatible" :api-key "grover" :base-url "https://api.x.ai/v1"
                      :name "grok" :simulate-provider "grok"}
    nil))

(defn- provider-config->wire-config [provider-config]
  (cond-> provider-config
    (:api-key provider-config)                               (assoc :apiKey (:api-key provider-config))
    (:auth-key provider-config)                              (assoc :authKey (:auth-key provider-config))
    (:assistant-base-url provider-config)                    (assoc :assistantBaseUrl (:assistant-base-url provider-config))
    (:base-url provider-config)                              (assoc :baseUrl (:base-url provider-config))
    (:response-format provider-config)                       (assoc :responseFormat (:response-format provider-config))
    (contains? provider-config :stream-supports-tool-calls)  (assoc :streamSupportsToolCalls (:stream-supports-tool-calls provider-config))
    (contains? provider-config :supports-system-role)        (assoc :supportsSystemRole (:supports-system-role provider-config))))

(defn- real-provider-defaults [provider]
  (case provider
    "openai-codex"   {:api "openai-compatible" :auth "oauth-device" :name "openai-chatgpt"}
    "openai-chatgpt" {:api "openai-compatible" :auth "oauth-device" :name "openai-chatgpt"}
    nil))

(defn- normalize [provider provider-config]
  (if-let [target (simulated-provider-target provider)]
    [target (merge (simulated-provider-config target) (or provider-config {}))]
    (if-let [defaults (real-provider-defaults provider)]
      [provider (merge defaults (or provider-config {}))]
      [provider provider-config])))

(defn resolve-api
  "Resolve a (name, config) to its api string, or nil if unknown."
  [provider provider-config]
  (let [[provider provider-config] (normalize provider provider-config)]
    (or (:api provider-config)
        (cond
          (= provider "claude-sdk")               "claude-sdk"
          (= provider "grover")                   "grover"
          (str/starts-with? provider "anthropic") "anthropic-messages"
          (= provider "ollama")                   "ollama"
          :else                                     nil))))

(defn ollama-opts [provider-config]
  {:base-url    (or (:base-url provider-config) "http://localhost:11434")
   :session-key (:session-key provider-config)})

(defn wire-opts
  "Wire-config map suitable for impl chat fns — i.e. {:provider-config wire-config}."
  [provider-config]
  {:provider-config (provider-config->wire-config provider-config)})

(defn normalize-pair [name provider-config]
  (normalize name provider-config))

(defn api-of
  "Resolve a Provider instance's api string. Equivalent to resolve-api on
   (display-name, config) — convenient for callers that need to dispatch
   on api family (e.g., choosing the anthropic prompt builder)."
  [p]
  (resolve-api (display-name p) (config p)))
