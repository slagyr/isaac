---
# isaac-uxe1
title: chat-completions adapter puts Isaac's internal keys on the wire; strict providers reject the request
status: todo
type: bug
tags:
    - llm
    - providers
created_at: 2026-09-21T02:31:44Z
updated_at: 2026-09-21T02:31:44Z
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
