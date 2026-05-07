(ns isaac.provider
  (:require
    [isaac.llm.api :as api]))

(def Api api/Api)
(def Provider api/Api)

(def chat api/chat)
(def chat-stream api/chat-stream)
(def followup-messages api/followup-messages)
(def config api/config)
(def display-name api/display-name)

(def tool-call api/tool-call)
(def usage api/usage)
(def assistant-message api/assistant-message)
(def response api/response)
(def error-response api/error-response)

(def error? api/error?)
(def validate-response api/validate-response)
(def resolve-api api/resolve-api)
(def ollama-opts api/ollama-opts)
(def wire-opts api/wire-opts)
(def normalize-pair api/normalize-pair)
(def api-of api/api-of)
(def register! api/register!)
(def unregister! api/unregister!)
(def factory-for api/factory-for)
(def registered-apis api/registered-apis)
