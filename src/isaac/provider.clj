(ns isaac.provider
  "Protocol for an LLM provider — the gateway to a thinking-engine
   (Anthropic, OpenAI, Ollama, Claude SDK, Grover test stub).

   Implementations live alongside their wire code in isaac.llm.<name>.
   isaac.drive.dispatch resolves a (provider-name, provider-config)
   pair to a concrete Provider via make-provider.")

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
     between chat iterations."))
