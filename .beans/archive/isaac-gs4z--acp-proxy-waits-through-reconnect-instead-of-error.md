---
# isaac-gs4z
title: "ACP proxy waits through reconnect instead of erroring requests"
status: completed
type: bug
priority: normal
created_at: 2026-04-28T01:04:20Z
updated_at: 2026-04-28T03:17:24Z
---

## Description

When the ACP proxy is disconnected from the remote server, src/isaac/cli/acp.clj currently returns a JSON-RPC error -32099 with message 'remote connection lost, reconnecting' for incoming requests. Toad appears to treat that as a fatal agent failure. Change the proxy so transient disconnects are surfaced as agent_thought_chunk status notifications only; requests should wait for reconnect and then be forwarded, instead of failing immediately. Preserve the existing reconnect notifications and make the status text newline-delimited so clients that concatenate thought chunks still render readable output.

## Notes

ACP proxy requests now wait through transient disconnects, reconnect/disconnect status remains notification-based with newline-delimited chunks, and the ACP feature harness now preserves interleaved responses while matching notifications. bb spec && bb features pass.

