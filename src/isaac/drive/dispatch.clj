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
    [isaac.module.loader :as module-loader]
    [isaac.provider :as provider]))

(def built-in-providers registry/built-in-providers)

(defonce _boot
  (do (anthropic/-isaac-init)
      (claude-sdk/-isaac-init)
      (grover/-isaac-init)
      (ollama/-isaac-init)
      (openai-compat/-isaac-init)))

(def resolve-api provider/resolve-api)

(defn make-provider
  "Resolve (name, config) to a Provider instance via the open registry.
   Each provider impl namespace registers a factory at load time
   (see e.g. isaac.llm.anthropic). Throws on unknown api — honest
   error beats silent fallback to ollama."
  [name provider-config]
  (let [original-name name
        [name cfg]    (provider/normalize-pair name provider-config)
        api           (provider/resolve-api name cfg)
        factory       (or (provider/factory-for api)
                          (when-let [module-id (module-loader/supporting-module-id (:module-index cfg) :provider api)]
                            (module-loader/activate! module-id (:module-index cfg))
                            (provider/factory-for api)))]
    (when-not factory
      (throw (ex-info (str "Unknown provider: " (pr-str original-name))
                      {:provider           original-name
                       :provider-config    provider-config
                       :api                api
                       :registered         (provider/registered-apis)})))
    (factory original-name cfg)))

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
