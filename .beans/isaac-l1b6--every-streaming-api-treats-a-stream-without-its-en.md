---
# isaac-l1b6
title: Every streaming API treats a stream without its end marker as weather
status: draft
type: bug
priority: high
created_at: 2026-10-05T14:29:07Z
updated_at: 2026-10-05T14:29:07Z
---

Micah, 2026-10-05. On the night of 10-04, chatgpt closed seven streams empty 1–4 s after the request (zanebot: isaac-work-1/2/4 mid-turn, tempest vault-sync cron). The responses adapter reported each as a plain `:llm-error` ("responses stream ended without response.completed"), so the drive ended the turn: no fallback (scrapper has `:model-fallback [:grok-4-6 :micah-opus]`), no suspend, no retry; the hailed beans sat claimed with no worker.

Audit of the streaming adapters (isaac-agent `llm/api/`):
- responses: catches only an *empty* cut (`incomplete-responses-stream?`); a cut after some text passes as a response.
- messages: never checks `message_stop`; a missing `stop_reason` maps to `:other` and partial text returns as complete.
- chat_completions: never requires `finish_reason`; same `:other` pass-through.
- ollama: reads `done` but doesn't fail when it never arrives.

## Shape
- **Adapters report the fact.** Each streaming adapter returns `{:error :stream-ended-early}` when the stream closes before its end marker (`response.completed` / `message_stop` / `finish_reason` / `done true`), empty or partial. Nothing received is kept: no partial content, no tool calls.
- **The drive classifies it as weather.** `drive/provider_wall.clj` adds `:stream-ended-early` to `fallback-reasons` beside `:stream-stalled`, so the existing behavior applies: next model in the chain, else suspend with backoff (30 s growing, no provider Retry-After) and resume.
- **Grover fixture:** a queued `cut-off` response streams its content/tool call and stops before the API's end marker; complete responses now send each API's real end marker (messages currently omits `message_stop`).
- Out of scope: the claude-code CLI provider (not a streaming HTTP adapter).

Likely repo scope: isaac-agent.
