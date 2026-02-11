(ns mdm.isaac.llm.core
  "LLM abstraction layer - configurable providers via multimethod dispatch.
   Supports Ollama, Grok, and other LLM backends."
  (:require [mdm.isaac.config :as config]))

(defn chat-impl
  "Returns the configured LLM implementation keyword."
  []
  (get-in config/active [:llm :impl] :ollama))

(defn chat-with-tools-impl
  "Returns the configured LLM implementation keyword for tool-enabled chat."
  []
  (get-in config/active [:llm :impl] :ollama))

(defmulti chat
  "Send a prompt to the configured LLM, returns response text.
   Dispatches based on :llm :impl in config."
  (fn [_prompt] (chat-impl)))

(defmulti chat-with-tools
  "Send messages with tool definitions to the configured LLM.
   messages: vector of {:role \"...\" :content \"...\"} maps
   tools: vector of tool definitions in OpenAI function-calling format
   Returns {:content \"...\" :tool-calls [{:name \"...\" :arguments {...}}]}
   Dispatches based on :llm :impl in config."
  (fn [_messages _tools] (chat-with-tools-impl)))

(defmethod chat :default [_prompt]
  (throw (ex-info "Unknown LLM implementation" {:impl (chat-impl)})))

(defmethod chat-with-tools :default [_messages _tools]
  (throw (ex-info "Unknown LLM implementation" {:impl (chat-with-tools-impl)})))
