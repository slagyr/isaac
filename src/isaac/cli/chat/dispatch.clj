(ns isaac.cli.chat.dispatch
  (:require
    [clojure.string :as str]
    [isaac.llm.anthropic :as anthropic]
    [isaac.llm.claude-sdk :as claude-sdk]
    [isaac.llm.grover :as grover]
    [isaac.llm.ollama :as ollama]
    [isaac.llm.openai-compat :as openai-compat]
    [isaac.logger :as log]))

(defn- simulated-provider-target [provider]
  (when (str/starts-with? provider "grover:")
    (subs provider (count "grover:"))))

(defn- simulated-provider-config [provider]
  (case provider
    "openai"       {:api               "openai-compatible"
                     :apiKey            "grover"
                     :baseUrl           "https://api.openai.com/v1"
                     :name              "openai"
                     :simulate-provider "openai"}
    "openai-codex" {:api               "openai-compatible"
                     :auth              "oauth-device"
                     :baseUrl           "https://api.openai.com/v1"
                     :name              "openai-codex"
                     :simulate-provider "openai-codex"}
    "grok"         {:api               "openai-compatible"
                     :apiKey            "grover"
                     :baseUrl           "https://api.x.ai/v1"
                     :name              "grok"
                     :simulate-provider "grok"}
    nil))

(defn- normalize-provider [provider provider-config]
  (if-let [target (simulated-provider-target provider)]
    [target (merge (simulated-provider-config target) (or provider-config {}))]
    [provider provider-config]))

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
  {:base-url    (or (:baseUrl provider-config) "http://localhost:11434")
   :session-key (:session-key provider-config)})

(defn- provider-chat [provider provider-config request]
  (let [[provider provider-config] (normalize-provider provider provider-config)]
    (case (resolve-api provider provider-config)
      "claude-sdk"         (claude-sdk/chat request)
      "grover"             (grover/chat request {:provider-config provider-config})
      "anthropic-messages" (anthropic/chat request {:provider-config provider-config})
      "openai-compatible"  (openai-compat/chat request {:provider-config provider-config})
      (ollama/chat request (ollama-opts provider-config)))))

(defn- provider-chat-stream [provider provider-config request on-chunk]
  (let [[provider provider-config] (normalize-provider provider provider-config)]
    (case (resolve-api provider provider-config)
      "claude-sdk"         (claude-sdk/chat-stream request on-chunk)
      "grover"             (grover/chat-stream request on-chunk {:provider-config provider-config})
      "anthropic-messages" (anthropic/chat-stream request on-chunk {:provider-config provider-config})
      "openai-compatible"  (openai-compat/chat-stream request on-chunk {:provider-config provider-config})
      (ollama/chat-stream request on-chunk (ollama-opts provider-config)))))

(defn- provider-chat-with-tools [provider provider-config request tool-fn]
  (let [[provider provider-config] (normalize-provider provider provider-config)]
    (case (resolve-api provider provider-config)
      "claude-sdk"         (provider-chat provider provider-config request)
      "grover"             (grover/chat-with-tools request tool-fn {:provider-config provider-config})
      "anthropic-messages" (anthropic/chat-with-tools request tool-fn {:provider-config provider-config})
      "openai-compatible"  (openai-compat/chat-with-tools request tool-fn {:provider-config provider-config})
      (ollama/chat-with-tools request tool-fn (ollama-opts provider-config)))))

(defn- response-preview [result]
  (let [content    (or (get-in result [:message :content])
                       (get-in result [:response :message :content]))
        tool-calls (or (get-in result [:message :tool_calls])
                       (get-in result [:response :message :tool_calls]))
        preview    (when (string? content)
                     (subs content 0 (min 200 (count content))))]
    (cond-> {}
      preview    (assoc :content-preview preview)
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
                       (provider-chat provider provider-config request)
                       :chat/error
                       :chat/response))

(defn dispatch-chat-stream [provider provider-config request on-chunk]
  (log/debug :chat/stream-request :provider provider :model (:model request))
  (log-dispatch-result provider
                       (provider-chat-stream provider provider-config request on-chunk)
                       :chat/stream-error
                       :chat/stream-response))

(defn dispatch-chat-with-tools [provider provider-config request tool-fn]
  (log/debug :chat/request-with-tools :provider provider :model (:model request))
  (log-dispatch-result provider
                       (provider-chat-with-tools provider provider-config request tool-fn)
                       :chat/error
                       :chat/response))
