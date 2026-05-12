---
# isaac-g3n
title: "Forward --model, --agent, --resume to remote server via WS query params"
status: completed
type: feature
priority: normal
created_at: 2026-04-13T03:00:17Z
updated_at: 2026-04-13T03:03:41Z
---

## Description

## Problem

CLI flags `--model`, `--agent`, and `--resume` are ignored when using `--remote`. The remote server always uses its own defaults.

## Design

The proxy builds a query string from CLI flags and appends it to the WebSocket URL:
```
--remote ws://host:6674/acp --model grok --agent ketch
→ connects to ws://host:6674/acp?model=grok&agent=ketch
```

The server reads query params from the WebSocket upgrade request and applies them to the handler opts.

### Loopback testing
The loopback factory receives the full URL including query string. It parses the params and makes them available to the server handler as connection metadata — simulating what HTTP would do.

## Acceptance

- `bb features features/acp/proxy.feature` passes with @wip removed from all 3 new scenarios
- @wip removed
- Manual: `isaac chat --remote ws://host:6674/acp --model grok` uses grok on the remote server

## Acceptance Criteria

All 3 @wip proxy forwarding scenarios pass with @wip removed. Manual: remote model/agent override works.

