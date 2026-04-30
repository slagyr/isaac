(ns isaac.drive.dispatch
  (:require
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

(def resolve-api provider/resolve-api)

(defn make-provider
  "Resolve (name, config) to a Provider instance. Throws on unknown
   provider — honest error beats silent fallback to ollama."
  [name provider-config]
  (let [original-name name
        [name cfg]    (provider/normalize-pair name provider-config)
        wire-opts     (provider/wire-opts cfg)
        api           (provider/resolve-api name cfg)]
    (case api
      "claude-sdk"         (claude-sdk/->ClaudeSdkProvider original-name cfg)
      "grover"             (grover/->GroverProvider original-name wire-opts cfg)
      "anthropic-messages" (anthropic/->AnthropicProvider original-name wire-opts cfg)
      "openai-compatible"  (openai-compat/->OpenAICompatProvider original-name wire-opts cfg)
      "ollama"             (ollama/->OllamaProvider original-name (provider/ollama-opts cfg) cfg)
      (throw (ex-info (str "Unknown provider: " (pr-str original-name))
                      {:provider original-name :provider-config provider-config :api api})))))

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

(defn dispatch-chat [p request]
  (let [name (provider/display-name p)]
    (log/debug :chat/request :provider name :model (:model request))
    (log-dispatch-result name (provider/chat p request) :chat/error :chat/response)))

(defn dispatch-chat-stream [p request on-chunk]
  (let [name (provider/display-name p)]
    (log/debug :chat/stream-request :provider name :model (:model request))
    (log-dispatch-result name (provider/chat-stream p request on-chunk)
                         :chat/stream-error :chat/stream-response)))

(defn dispatch-chat-with-tools
  "Run a tool-call loop for this provider. Composed from Provider/chat
   and Provider/followup-messages."
  [p request tool-fn]
  (let [name (provider/display-name p)]
    (log/debug :chat/request-with-tools :provider name :model (:model request))
    (log-dispatch-result name
                         (tool-loop/run #(provider/chat p %)
                                        #(provider/followup-messages p %1 %2 %3 %4)
                                        request
                                        tool-fn)
                         :chat/error :chat/response)))
