---
# isaac-1yb5
title: "LLM reorg B: split openai-compat into openai-completions and openai-responses"
status: completed
type: task
priority: normal
created_at: 2026-05-07T05:28:30Z
updated_at: 2026-05-07T15:59:06Z
---

## Description

Under umbrella isaac-uwso. Depends on A (isaac-4ca2).

Today, isaac.llm.openai-compat handles two distinct OpenAI wire specs on a runtime branch:
- Chat Completions (api.openai.com/v1/chat/completions) — chat-completions-request? path
- Responses (api.openai.com/v1/responses, also chatgpt.com/backend-api/codex) — ->responses-request, ->codex-responses-request, process-responses-sse-event

Split into:
- isaac.llm.api.openai-completions — Chat Completions wire only
- isaac.llm.api.openai-responses — Responses wire only (covers both api.openai.com responses and codex)

Shared helpers (auth, JWT decode, account-id extraction) move to a small shared ns (isaac.llm.api.openai.shared or similar) — no duplication.

Provider catalog (sub-bead C) maps known providers (openai, openai-codex, openai-chatgpt, grok) to whichever api id is appropriate. Today's 'openai-compatible' api id retires.

## Acceptance Criteria

openai-compat ns gone; two separate api impls registered; all openai-using providers (openai, openai-codex, grok, openai-chatgpt) keep working; mutation-test header (;; mutation-tested: ...) carried over to whichever ns the heavy logic lands in; bb spec and bb features green.

## Notes

Verification failed: bb spec and bb features are green, openai-compat source ns is gone, the new split namespaces exist and register (:openai-completions and :openai-responses), and provider mapping still covers openai/openai-codex/grok/openai-chatgpt. However the acceptance criterion requiring the mutation-test header to be carried over is not met: neither src/isaac/llm/api/openai_completions.clj nor src/isaac/llm/api/openai_responses.clj nor src/isaac/llm/api/openai/shared.clj contains a ;; mutation-tested: ... marker.

