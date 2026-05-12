---
# isaac-hms
title: "ACP: JSON-RPC stdio infrastructure"
status: completed
type: task
priority: normal
created_at: 2026-04-10T21:09:31Z
updated_at: 2026-04-10T21:19:09Z
---

## Description

Build the foundation for ACP: JSON-RPC 2.0 message framing over stdin/stdout.

## Scope
- New namespace `isaac.acp.rpc`
- Reader: line-delimited JSON from stdin, parse to message map
- Writer: serialize message map, write + newline + flush to stdout
- Dispatch: map method name → handler fn, returns response or nil for notifications
- Error responses for malformed JSON (-32700), unknown method (-32601), invalid params (-32602)
- Testable via in-memory streams (don't require a subprocess)

## Step definitions to add
In spec/isaac/features/steps/acp.clj:
- `the ACP client sends request {int}:` — build JSON with jsonrpc+id+table, call dispatch, don't wait
- `the ACP client sends notification:` — same without id
- `the ACP agent sends response {int}:` — await response with matching id, match vertical table
- `the ACP agent sends notifications:` — await emitted notifications, match horizontal table
- `the ACP client has initialized` — shorthand for send init request, await response, discard

## Additional step change
- `the following model responses are queued:` — parse content column as EDN vector for streaming chunks, single string otherwise (needed for features/acp/streaming.feature)

Parent epic: isaac-new
Feature files (all @wip): features/acp/*.feature

## Definition of Done
- RPC infrastructure handles parse/dispatch/error paths
- All new step definitions registered
- bb features passes (all ACP scenarios still @wip at this stage)
- bb spec passes

