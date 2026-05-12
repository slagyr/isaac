---
# isaac-8vt
title: "ACP tool notifications: add sessionId, flush in real time"
status: completed
type: bug
priority: high
created_at: 2026-04-12T14:21:23Z
updated_at: 2026-04-12T14:31:20Z
---

## Description

## Problem

Tool call notifications in ACP are broken in two ways:

1. **Missing sessionId** — `tool_call` and `tool_call_update` notifications omit `:sessionId` from params, while text chunk notifications include it. Toad likely drops them.

2. **Batched, not streamed** — `run-prompt` in `acp/server.clj` collects all notifications into an atom, then `write-result!` in `cli/acp.clj` dumps them all to stdout AFTER the turn completes. Toad never sees tool calls in real time.

## Fix

### sessionId (src/isaac/channel/acp.clj)
Add `:sessionId` to `tool-call-notification` and `tool-result-notification` params. The channel already receives session-key via `on-tool-call` and `on-tool-result`.

### Real-time flushing
Redesign the ACP channel to write directly to the output stream instead of buffering into an atom. The channel constructor should accept the output writer. Each `on-text-chunk`, `on-tool-call`, and `on-tool-result` call writes and flushes immediately.

This means `run-prompt` in `acp/server.clj` no longer returns `:notifications` — they've already been sent. It only returns the final `:result` map. `write-result!` in `cli/acp.clj` simplifies accordingly.

## New step definition needed
- `Then the output lines contain in order:` — verifies patterns appear in sequence in stdout

## Acceptance

- `bb features features/acp/tools.feature` passes with @wip removed from both new scenarios
- `bb features features/cli/acp.feature` passes with @wip removed from the ordering scenario
- @wip removed from all three scenarios
- Manual: `isaac chat --toad`, ask to run a command, Toad shows tool execution in real time

## Acceptance Criteria

All three @wip tool notification scenarios pass with @wip removed. Manual: Toad shows tool calls as they happen.

