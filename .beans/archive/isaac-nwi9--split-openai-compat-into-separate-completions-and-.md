---
# isaac-nwi9
title: "Split openai-compat into separate completions and responses providers"
status: completed
type: task
priority: normal
created_at: 2026-05-05T22:57:04Z
updated_at: 2026-05-07T19:27:56Z
---

## Description

Why: today, 'openai-compat' is one provider name where the :api config field selects between the OpenAI Completions API and the OpenAI Responses API. The two APIs are genuinely different — different request shapes, different response shapes, different capabilities (Responses adds reasoning, file search, server-side state). The single-provider-with-branching-:api design forces chat/chat-stream/followup-messages to branch internally on every call.

Pulling them apart into two distinct providers cleans up both the implementation and the user-facing config.

## Scope

- Create two providers in src/isaac/llm/:
  - openai-compat-completions (or openai-completions) — speaks the Chat Completions API
  - openai-compat-responses (or openai-responses) — speaks the Responses API
- Each implements isaac.provider/Provider with its API-specific chat/chat-stream/followup-messages, no internal :api branching.
- Update isaac.provider/resolve-api so each provider name maps directly to its api string (drop the implicit default that requires :api in config).
- Update isaac.llm.registry/built-in-providers to list the new names.
- Update cfg fixtures, scenarios, and any docs that reference 'openai-compat'.
- Existing user configs that say {:provider :openai-compat :api :openai-compat-responses} need a migration path — either auto-coerce to the new provider name, or hard-error with a migration hint.

## Out of scope

- Other provider splits.
- New API features in either provider.
- Renaming :api strings in the registry (keep current names; just align provider names with them).

## Acceptance

- src/isaac/llm/ has separate namespaces for each API; no :api-branching inside chat/chat-stream/followup-messages.
- isaac.llm.registry/built-in-providers lists the new provider names; openai-compat is no longer a single provider.
- Existing OpenAI-flavored scenarios pass without an :api config field — provider name alone selects the API.
- Migration path for legacy configs: documented, and either auto-coerced or hard-errored with a hint.

## Acceptance Criteria

openai-compat-completions and openai-compat-responses are separate providers; chat/chat-stream/followup-messages no longer branch on :api; built-in-providers updated; legacy config has a migration path; tests pass

## Notes

Superseded by isaac-1yb5. The provider split is already landed: openai-completions/openai-responses impl namespaces exist, openai-compat is gone from src/spec, and provider selection is direct via the catalog. Any remaining work would be a smaller follow-up for legacy config migration only.

