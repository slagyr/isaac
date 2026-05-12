---
# isaac-twl
title: "Support OpenAI responses API for OAuth providers (grover dialect simulation)"
status: completed
type: feature
priority: high
created_at: 2026-04-15T16:51:47Z
updated_at: 2026-04-15T18:25:55Z
---

## Description

OpenAI Codex OAuth uses chatgpt.com/backend-api/codex/responses, NOT api.openai.com/v1/responses. Discovered via mitmproxy interception of OpenCode traffic.

Key differences from the standard OpenAI API:
- Base URL: https://chatgpt.com/backend-api/codex
- Headers: ChatGPT-Account-Id (from token), originator: isaac
- Body: requires instructions field (the soul), stream must be true
- Streaming only — no non-streaming path

Also need grover dialect simulation (grover:openai-codex, grover:openai) for fast testing.

features/providers/openai/dispatch.feature — 3 scenarios

## Acceptance Criteria

All 3 @wip scenarios pass with @wip removed. Marvin works on GPT-5.3-codex via OAuth in IDEA.

