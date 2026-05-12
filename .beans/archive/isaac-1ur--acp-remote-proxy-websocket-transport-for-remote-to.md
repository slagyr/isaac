---
# isaac-1ur
title: "ACP remote proxy: WebSocket transport for remote Toad sessions"
status: completed
type: feature
priority: normal
created_at: 2026-04-12T16:59:06Z
updated_at: 2026-04-12T17:19:13Z
---

## Description

## Problem

Isaac's ACP is stdio-only. To chat with Isaac from another machine via Toad, we need a network transport.

## Design

### Architecture
```
[Remote Machine]              [Host Machine]
Toad -> isaac acp --remote -> WS -> isaac server /acp -> handlers
        (stdio<->WS proxy)         (WS<->handlers bridge)
```

### WebSocket transport abstraction
A `WsConnection` protocol with `ws-send!`, `ws-receive!`, `ws-close!` methods. Two implementations:
- **LoopbackWs**: in-memory queues connecting client and server directly. Used by tests.
- **RealWs**: actual WebSocket over HTTP. Used in production. Thin wrapper around http-kit.

### Client side (`src/isaac/cli/acp.clj`)
- `--remote URL` flag triggers proxy mode
- `--token TOKEN` flag for authentication
- Reads stdin, forwards each line to WsConnection. Reads from WsConnection, writes to stdout.
- Connection errors produce clear stderr messages.

### Server side (`src/isaac/server/`)
- `/acp` WebSocket endpoint on the existing HTTP server
- Auth: validates `Authorization: Bearer <token>` header during WS upgrade
- Token configured via `gateway.auth.token` in config (OpenClaw-compatible)
- Dispatches WS messages to existing ACP handlers
- Writes responses and notifications back over WS

### Toad integration (`src/isaac/cli/chat/toad.clj`)
- `--remote` flag passes through to the `isaac acp` subprocess command

### New step definitions needed
- `Given the ACP proxy is connected via loopback` — wires client/server via LoopbackWs
- `Then the output contains a JSON-RPC response for id N:` (table) — structured assertion on stdout
- `Then the output lines contain in order:` (table) — ordered pattern matching on stdout
- `Given the Isaac server is running` — starts real HTTP server for @slow tests
- `#*` wildcard in table values — matches any non-nil value

## Acceptance

- `bb features features/acp/proxy.feature` — all 5 loopback scenarios pass with @wip removed
- `bb features features/server/acp_websocket.feature` — both auth scenarios pass with @wip removed
- `bb features features/chat/toad.feature` — --remote dry-run scenario passes with @wip removed
- @wip removed from all 8 scenarios
- Manual: `isaac server` on host, `isaac chat --toad --remote ws://host:6674/acp --token T` from another machine, full chat session works

## Acceptance Criteria

All 8 @wip scenarios pass with @wip removed. Manual: remote Toad session works end-to-end.

## Design

WebSocket transport abstracted via WsConnection protocol. LoopbackWs for tests (in-memory queues, no network). RealWs for production. Auth at HTTP layer via gateway.auth.token config.

