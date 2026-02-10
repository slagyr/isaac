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
  "Send a prompt with tool definitions to the configured LLM.
   Returns {:content \"...\" :tool-calls [{:name \"...\" :arguments {...}}]}
   Dispatches based on :llm :impl in config."
  (fn [_prompt _tools] (chat-with-tools-impl)))

(defmethod chat :default [_prompt]
  (throw (ex-info "Unknown LLM implementation" {:impl (chat-impl)})))

(defmethod chat-with-tools :default [_prompt _tools]
  (throw (ex-info "Unknown LLM implementation" {:impl (chat-with-tools-impl)})))
