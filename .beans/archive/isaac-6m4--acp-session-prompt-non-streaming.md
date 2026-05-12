---
# isaac-6m4
title: "ACP: session/prompt (non-streaming)"
status: completed
type: task
priority: normal
created_at: 2026-04-10T21:10:03Z
updated_at: 2026-04-10T21:35:16Z
---

## Description

Handle session/prompt by driving Isaac's existing chat flow and returning a stop reason when the turn completes. No streaming yet — the full response is stored and the final result returned.

## Scope
- Handler for `session/prompt`: extract text from prompt[0], look up Isaac session by sessionId, call process-user-input! (or equivalent), await completion
- Return `{stopReason: 'end_turn'}` on success, `{stopReason: 'error', ...}` on provider error
- The response ID must match the request ID

Parent epic: isaac-new
Feature file: features/acp/prompt.feature (2 @wip scenarios)

## Acceptance
Remove @wip from both scenarios and verify:
- bb features features/acp/prompt.feature:19
- bb features features/acp/prompt.feature:38

Full suite: bb features and bb spec pass.

