---
# isaac-2ax
title: "Add WebSocket event logging and fix integration test port leaks"
status: completed
type: task
priority: normal
created_at: 2026-04-13T02:41:12Z
updated_at: 2026-04-13T03:26:26Z
---

## Description

## Problem

1. The ACP WebSocket handler in `src/isaac/server/acp_websocket.clj` logs nothing — no visibility into WS events.
2. The proxy client in `src/isaac/cli/acp.clj` uses generic `:ws/message-sent` and `:ws/message-received` events with nil methods.
3. Integration tests used a fixed port which can collide with real servers and leak processes.

## Fix

### Server logging (`acp_websocket.clj`)
One event per ACP method dispatched:
- `:acp-ws/initialize`
- `:acp-ws/session-new` with `:sessionId`
- `:acp-ws/session-prompt` with `:sessionId`
- `:acp-ws/session-cancel` with `:sessionId`
- `:acp-ws/connection-opened` and `:acp-ws/connection-closed` for lifecycle

### Proxy logging (`cli/acp.clj`)
Mirror events from the client side:
- `:acp-proxy/connected` and `:acp-proxy/disconnected` for lifecycle
- `:acp-proxy/initialize`, `:acp-proxy/session-new`, `:acp-proxy/session-prompt` for each forwarded method

### Integration test safety
- Port 0 (ephemeral) — OS assigns a random available port
- `${server.port}` substitution resolves to actual port after startup
- Step definition must shut down the server in an After hook

## Acceptance
- Loopback proxy scenario passes with `:acp-ws/` and `:acp-proxy/` log assertions
- Integration tests use ephemeral ports and shut down cleanly
- No leaked server processes after test runs

## Acceptance Criteria

WS log entries present in loopback tests. Integration tests use ephemeral ports. No leaked processes.

