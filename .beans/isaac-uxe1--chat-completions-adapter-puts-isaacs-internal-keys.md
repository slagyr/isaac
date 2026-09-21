---
# isaac-uxe1
title: chat-completions adapter puts Isaac's internal keys on the wire; strict providers reject the request
status: completed
type: bug
priority: normal
tags:
    - llm
    - providers
created_at: 2026-09-21T02:31:44Z
updated_at: 2026-09-21T02:40:32Z
---

`isaac.llm.api.chat-completions` POSTs the request map to `/chat/completions`
almost verbatim — it strips only `:effort`. Every internal key Isaac put on that
map goes out as a JSON field.

`build-chat-request` (isaac-agent `drive/turn.clj:1196`) adds at least
`:session-key` and `:stateful`, and `:provider` reaches the body too.

## Observed 2026-09-21 (Fireworks, GLM 5.3)

    Provider fireworks is broken model accounts/fireworks/models/glm-5p3
    session isaac-work-1
    2 request validation errors:
      Extra inputs are not permitted, field: 'session-key', value: 'isaac-work-1';
      Extra inputs are not permitted, field: 'provider',    value: 'fireworks'

Fireworks validates its request body strictly (Pydantic) and refuses unknown
fields. OpenAI and xAI ignore them, which is why this has never surfaced — the
bug has always been there, hidden by lenient servers.

## Why it matters beyond Fireworks

- Any strict OpenAI-compatible endpoint is unusable with this adapter.
- `:session-key` is **session metadata leaving the machine** in every request to
  every chat-completions provider. That is a privacy leak, not only a
  correctness bug.

## Same shape elsewhere

`isaac.llm.api.ollama` builds its body the same way
(`(-> request (dissoc :effort) (assoc :stream false))`, ollama.clj:65,96) and
would leak the same keys. Check `responses.clj` too — it dissocs only in narrow
spots (`:raw-args`, `:tools`).

## Work

Build the wire body from a **whitelist** of OpenAI Chat Completions fields
rather than passing the caller's map through. Both paths — `chat-with-completions-api`
(chat_completions.clj:47) and `chat-stream-with-completions-api` (:64).

## Acceptance

- a chat-completions request body contains only OpenAI-recognised fields;
  `:session-key`, `:provider`, `:stateful`, `:root`, `:context-window` never appear
- `:effort` still maps to `reasoning_effort` (unchanged)
- streaming and non-streaming send the same field set, plus `stream`
- a provider that rejects unknown fields completes a turn
- the same audit is applied to the ollama adapter (or a sibling bean filed)


## Summary of Changes (2026-09-21, main-sha 4cd20fc, agent 0.1.76)

Both adapters now project the request onto a whitelist of fields their API
accepts, instead of POSTing the caller's map.

- `chat_completions.clj` — `wire-fields` / `wire-body`, applied to both the
  streaming and non-streaming paths. Kebab keys map to wire names
  (`:max-tokens` -> `max_tokens`). `:system` is dropped deliberately: the prompt
  builder already emits it as a `{:role "system"}` entry in `:messages`
  (`prompt/builder.clj:383`), so it was redundant.
- `ollama.clj` — same shape against Ollama's native `/api/chat` top-level fields
  (`model messages tools stream think format options keep_alive`).
  `:max-tokens` is **not** included: it is not a top-level Ollama field (the
  equivalent is `options.num_predict`), it was being sent as an unknown field and
  ignored, so dropping it changes nothing on the wire. Wiring Isaac's budget into
  `:options` stays with isaac-lrqo.

Specs assert the exact field set reaching the wire, both red before the fix:
`chat_completions_spec.clj` expects `#{:model :messages :max_tokens :reasoning_effort}`
from a request carrying `:session-key`, `:provider`, `:root`, `:stateful`,
`:system`; `ollama_spec.clj` expects `#{:model :messages :stream}` from the same.
CI green: 1660 + 841, 0 failures.

Note for the record: the privacy half of this mattered more than the Fireworks
half. `:session-key` was leaving the machine on every chat-completions and
Ollama request, to OpenAI and xAI included, for as long as the adapters have
existed. Lenient servers ignoring unknown fields is what kept it invisible.
