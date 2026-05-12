---
# isaac-5sd
title: "ACP proxy reconnect with backoff and Toad notifications"
status: completed
type: feature
priority: normal
created_at: 2026-04-13T03:43:08Z
updated_at: 2026-04-13T05:01:17Z
---

## Description

## Problem

If the remote server drops the WebSocket connection, the proxy exits immediately. The user loses their Toad session with no recovery.

## Design

### Reconnect behavior
- On connection drop, proxy enters reconnect loop with exponential backoff
- Retries up to `acp.proxy-max-reconnects` times (default configurable)
- On success, resumes normal operation — stdin/stdout keep flowing
- On max retries exhausted, exits with error

### Toad notifications
Connection state changes are sent as `session/update` notifications so Toad can display them:
- `Connection lost` — when drop detected
- `Reconnecting` — on each retry attempt
- `Reconnected` — when back up

### Loopback support
The loopback transport supports simulated drops:
- `the loopback connection drops` — closes the server side
- `the loopback connection is restored` — accepts new connections
- `the loopback connection drops permanently` — closes and refuses reconnects

### New step definitions
- `the acp proxy is running with {args}` — launches proxy on background thread
- `stdin receives:` — feeds lines to the running proxy
- `the loopback connection drops` / `is restored` / `drops permanently`

## Acceptance

- `bb features features/acp/reconnect.feature` passes with @wip removed (3 scenarios)
- @wip removed from all scenarios
- Manual: kill the server while Toad is connected, Toad shows 'Connection lost', restart server, Toad shows 'Reconnected' and resumes

## Acceptance Criteria

All 3 reconnect scenarios pass with @wip removed. Manual: Toad survives a server restart.

