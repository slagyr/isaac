---
# isaac-9ea
title: "ACP must not use stopReason:error — send errors as agent_message_chunk"
status: completed
type: bug
priority: high
created_at: 2026-04-15T21:38:19Z
updated_at: 2026-04-15T21:59:41Z
---

## Description

The ACP spec defines stopReason values: end_turn, max_tokens, refusal, cancelled. Isaac sends stopReason:error which is not in the spec. IDEA shows 'Internal error' for any unrecognized stopReason.

Fix: send the error message as an agent_message_chunk notification so the user sees it in the chat, then return stopReason:end_turn.

features/acp/error_response.feature — 2 scenarios

## Acceptance Criteria

Both @wip scenarios pass. IDEA shows error text in chat instead of 'Internal error'.

