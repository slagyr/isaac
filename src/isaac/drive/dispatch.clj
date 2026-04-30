(ns isaac.drive.dispatch
  (:require
    [clojure.string :as str]
    [isaac.llm.anthropic :as anthropic]
    [isaac.llm.claude-sdk :as claude-sdk]
    [isaac.llm.grover :as grover]
    [isaac.llm.ollama :as ollama]
    [isaac.llm.openai-compat :as openai-compat]
    [isaac.llm.registry :as registry]
    [isaac.logger :as log]))

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

(defn- provider-fns
  "Resolve a provider to its operations. Returns a map with :chat, :chat-stream,
   :chat-with-tools, and :followup-messages — each already partially applied
   with this provider's wire config and opts. Single case statement; adding a
   provider is one map literal."
  [provider provider-config]
  (let [[provider provider-config] (normalize-provider provider provider-config)
        wire-opts                  {:provider-config (provider-config->wire-config provider-config)}
        oo                         (ollama-opts provider-config)]
    (case (resolve-api provider provider-config)
      "claude-sdk"
      {:chat              (fn [req] (claude-sdk/chat req))
       :chat-stream       (fn [req on-chunk] (claude-sdk/chat-stream req on-chunk))
       :chat-with-tools   (fn [req _tool-fn] (claude-sdk/chat req))
       :followup-messages (fn [& _] (throw (ex-info "claude-sdk does not implement followup-messages" {:provider provider})))}

      "grover"
      {:chat              (fn [req] (grover/chat req wire-opts))
       :chat-stream       (fn [req on-chunk] (grover/chat-stream req on-chunk wire-opts))
       :chat-with-tools   (fn [req tool-fn] (grover/chat-with-tools req tool-fn wire-opts))
       :followup-messages grover/followup-messages}

      "anthropic-messages"
      {:chat              (fn [req] (anthropic/chat req wire-opts))
       :chat-stream       (fn [req on-chunk] (anthropic/chat-stream req on-chunk wire-opts))
       :chat-with-tools   (fn [req tool-fn] (anthropic/chat-with-tools req tool-fn wire-opts))
       :followup-messages anthropic/followup-messages}

      "openai-compatible"
      {:chat              (fn [req] (openai-compat/chat req wire-opts))
       :chat-stream       (fn [req on-chunk] (openai-compat/chat-stream req on-chunk wire-opts))
       :chat-with-tools   (fn [req tool-fn] (openai-compat/chat-with-tools req tool-fn wire-opts))
       :followup-messages openai-compat/followup-messages}

      ;; default: ollama
      {:chat              (fn [req] (ollama/chat req oo))
       :chat-stream       (fn [req on-chunk] (ollama/chat-stream req on-chunk oo))
       :chat-with-tools   (fn [req tool-fn] (ollama/chat-with-tools req tool-fn oo))
       :followup-messages ollama/followup-messages})))

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
  (let [chat-fn (:chat (provider-fns provider provider-config))]
    (log-dispatch-result provider (chat-fn request) :chat/error :chat/response)))

(defn dispatch-chat-stream [provider provider-config request on-chunk]
  (log/debug :chat/stream-request :provider provider :model (:model request))
  (let [stream-fn (:chat-stream (provider-fns provider provider-config))]
    (log-dispatch-result provider (stream-fn request on-chunk) :chat/stream-error :chat/stream-response)))

(defn dispatch-chat-with-tools [provider provider-config request tool-fn]
  (log/debug :chat/request-with-tools :provider provider :model (:model request))
  (let [tools-fn (:chat-with-tools (provider-fns provider provider-config))]
    (log-dispatch-result provider (tools-fn request tool-fn) :chat/error :chat/response)))

(defn provider-followup-messages
  "Build a provider-correct :messages vector for the next tool-loop iteration.
   Used by isaac.llm.tool-loop/run when the caller wires its own chat-fn
   (e.g. turn.clj's streaming path)."
  [provider provider-config request response tool-calls tool-results]
  (let [followup-fn (:followup-messages (provider-fns provider provider-config))]
    (followup-fn request response tool-calls tool-results)))
