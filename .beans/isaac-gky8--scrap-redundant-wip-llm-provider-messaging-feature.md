---
# isaac-gky8
title: Scrap redundant @wip LLM provider messaging features
status: completed
type: task
priority: low
created_at: 2026-06-15T22:48:48Z
updated_at: 2026-06-15T22:58:20Z
---

The 4 whole-file @wip provider *_messaging features have dangled @wip since before the extraction (confirmed
@wip in monolith @ 09795481). Per-scenario coverage audit -> ALL redundant. Scrap them. (@wip files generate
no test code, so deletion changes no run.)

## Coverage mapping (audit result)
anthropic_messaging.feature:
- "System prompt is a separate field"        -> messages_spec (request/framing-block + message-structure tests)
- "Prompt caching breakpoints are applied"   -> messages_spec "keeps framing block... breakpoint origin-free", "tracks cache tokens"
- "Tool call with Anthropic format"          -> messages_spec "extracts tool_use blocks", "wraps tool-calls in tool_use blocks"

ollama/messaging.feature:
- "Request uses Ollama chat format"          -> ollama_spec "sends request and parses response", "constructs correct URL", "sets stream false"

chat_completions/openai_messaging.feature:
- "Request uses OpenAI chat completions format" -> chat_completions_spec "parses text from choices", "constructs URL", "sets Bearer header"
- "Tool call with OpenAI format"             -> chat_completions_spec "extracts tool calls (string/map args)", "appends assistant tool_calls in OpenAI function format"

chat_completions/grok_messaging.feature  (NOT grok-specific — generic transcript behavior using grover:grok):
- "Provider-returned model version is stored in transcript" -> covered by ACTIVE features bridge/model.feature,
  session/llm_interaction.feature, session/identity.feature, ollama/integration.feature (all assert message.model/provider)
- "Configured model is stored when provider returns no model" -> GENERIC no-model fallback. turn_spec covers
  transcript model/usage handling. GUARD: confirm this exact fallback edge is asserted somewhere; if NOT, add ONE
  provider-agnostic it to drive/turn_spec, then scrap — do not keep a provider-specific @wip for a generic behavior.

## Action
Delete the 4 files:
- features/llm/api/messages/anthropic_messaging.feature
- features/llm/api/ollama/messaging.feature
- features/llm/api/chat_completions/openai_messaging.feature
- features/llm/api/chat_completions/grok_messaging.feature
(in isaac-agent)

## Acceptance
- The 4 @wip features removed.
- Each scrapped scenario's behavior confirmed asserted by its mapped unit spec / active feature (no coverage lost).
- grok no-model-fallback edge: confirmed covered, or a single generic drive/turn_spec it added.
- bb spec + bb features green (no change expected).

Note: pre-existing tech debt, separate from the extraction-coverage restore.
