(ns isaac.drive.dispatch
  (:require
    [clojure.string :as str]
    [isaac.llm.anthropic :as anthropic]
    [isaac.llm.claude-sdk :as claude-sdk]
    [isaac.llm.grover :as grover]
    [isaac.llm.ollama :as ollama]
    [isaac.llm.openai-compat :as openai-compat]
    [isaac.llm.registry :as registry]
    [isaac.llm.tool-loop :as tool-loop]
    [isaac.logger :as log]
    [isaac.provider :as provider]))

(def built-in-providers registry/built-in-providers)

(defn- simulated-provider-target [provider]
  (when (str/starts-with? provider "grover:")
    (subs provider (count "grover:"))))

(defn- simulated-provider-config [provider]
  (case provider
    "openai"       {:api               "openai-compatible"
                     :api-key           "grover"
                     :base-url          "https://api.openai.com/v1"
                     :name              "openai"
                     :simulate-provider "openai"}
    "openai-api"   {:api               "openai-compatible"
                      :api-key           "grover"
                      :base-url          "https://api.openai.com/v1"
                     :name              "openai-api"
                     :simulate-provider "openai-api"}
    "openai-codex" {:api               "openai-compatible"
                     :auth              "oauth-device"
                     :base-url          "https://api.openai.com/v1"
                     :name              "openai-chatgpt"
                     :simulate-provider "openai-chatgpt"}
    "openai-chatgpt" {:api               "openai-compatible"
                      :auth              "oauth-device"
                      :base-url          "https://api.openai.com/v1"
                      :name              "openai-chatgpt"
                      :simulate-provider "openai-chatgpt"}
    "grok"         {:api               "openai-compatible"
                      :api-key           "grover"
                      :base-url          "https://api.x.ai/v1"
                     :name              "grok"
                     :simulate-provider "grok"}
    nil))

(defn- provider-config->wire-config [provider-config]
  (cond-> provider-config
    (:api-key provider-config)                  (assoc :apiKey (:api-key provider-config))
    (:auth-key provider-config)                 (assoc :authKey (:auth-key provider-config))
    (:assistant-base-url provider-config)       (assoc :assistantBaseUrl (:assistant-base-url provider-config))
    (:base-url provider-config)                 (assoc :baseUrl (:base-url provider-config))
    (:response-format provider-config)          (assoc :responseFormat (:response-format provider-config))
    (contains? provider-config :stream-supports-tool-calls) (assoc :streamSupportsToolCalls (:stream-supports-tool-calls provider-config))
    (contains? provider-config :supports-system-role)       (assoc :supportsSystemRole (:supports-system-role provider-config))))

(defn- real-provider-defaults [provider]
  (case provider
    "openai-codex"   {:api "openai-compatible" :auth "oauth-device" :name "openai-chatgpt"}
    "openai-chatgpt" {:api "openai-compatible" :auth "oauth-device" :name "openai-chatgpt"}
    nil))

(defn- normalize-provider [provider provider-config]
  (if-let [target (simulated-provider-target provider)]
    [target (merge (simulated-provider-config target) (or provider-config {}))]
    (if-let [defaults (real-provider-defaults provider)]
      [provider (merge defaults (or provider-config {}))]
      [provider provider-config])))

(defn resolve-api [provider provider-config]
  (let [[provider provider-config] (normalize-provider provider provider-config)]
    (or (:api provider-config)
        (cond
          (= provider "claude-sdk")               "claude-sdk"
          (= provider "grover")                   "grover"
          (str/starts-with? provider "anthropic") "anthropic-messages"
          (= provider "ollama")                   "ollama"
          :else                                     "ollama"))))

(defn- ollama-opts [provider-config]
  {:base-url    (or (:base-url provider-config) "http://localhost:11434")
   :session-key (:session-key provider-config)})

(defn- make-provider
  "Resolve (provider-name, provider-config) to a Provider instance.
   Single case statement; adding a provider is one branch returning a deftype."
  [provider provider-config]
  (let [[name cfg] (normalize-provider provider provider-config)
        wire-opts  {:provider-config (provider-config->wire-config cfg)}]
    (case (resolve-api name cfg)
      "claude-sdk"         (claude-sdk/->ClaudeSdkProvider)
      "grover"             (grover/->GroverProvider wire-opts)
      "anthropic-messages" (anthropic/->AnthropicProvider wire-opts)
      "openai-compatible"  (openai-compat/->OpenAICompatProvider wire-opts)
      (ollama/->OllamaProvider (ollama-opts cfg)))))

(defn- response-preview [result]
  (let [content    (or (get-in result [:message :content])
                       (get-in result [:response :message :content]))
        tool-calls (or (get-in result [:message :tool_calls])
                       (get-in result [:response :message :tool_calls]))]
    (cond-> {}
      (string? content) (assoc :content-chars (count content))
      tool-calls (assoc :tool-calls-count (count tool-calls)))))

(defn- log-dispatch-result [provider result error-event response-event]
  (if (:error result)
    (log/error error-event :provider provider :error (:error result) :status (:status result))
    (log/debug response-event (merge {:provider provider :model (:model result)}
                                     (response-preview result))))
  result)

(defn dispatch-chat [provider provider-config request]
  (log/debug :chat/request :provider provider :model (:model request))
  (log-dispatch-result provider
                       (provider/chat (make-provider provider provider-config) request)
                       :chat/error :chat/response))

(defn dispatch-chat-stream [provider provider-config request on-chunk]
  (log/debug :chat/stream-request :provider provider :model (:model request))
  (log-dispatch-result provider
                       (provider/chat-stream (make-provider provider provider-config) request on-chunk)
                       :chat/stream-error :chat/stream-response))

(defn dispatch-chat-with-tools
  "Run a tool-call loop for this provider. Composed from Provider/chat
   and Provider/followup-messages."
  [provider provider-config request tool-fn]
  (log/debug :chat/request-with-tools :provider provider :model (:model request))
  (let [p (make-provider provider provider-config)]
    (log-dispatch-result provider
                         (tool-loop/run #(provider/chat p %)
                                        #(provider/followup-messages p %1 %2 %3 %4)
                                        request
                                        tool-fn)
                         :chat/error :chat/response)))

(defn provider-followup-messages
  "Build a provider-correct :messages vector for the next tool-loop iteration.
   Used by isaac.llm.tool-loop/run when the caller wires its own chat-fn
   (e.g. turn.clj's streaming path)."
  [provider provider-config request response tool-calls tool-results]
  (provider/followup-messages (make-provider provider provider-config)
                              request response tool-calls tool-results))
