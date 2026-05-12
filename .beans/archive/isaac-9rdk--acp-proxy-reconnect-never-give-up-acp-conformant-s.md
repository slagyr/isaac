---
# isaac-9rdk
title: "ACP proxy reconnect: never give up, ACP-conformant stdout"
status: completed
type: bug
priority: high
created_at: 2026-04-23T18:16:00Z
updated_at: 2026-04-23T19:13:09Z
---

## Description

Current proxy (src/isaac/cli/acp.clj) crashes Toad immediately on 'isaac server' restart:
- Writes malformed session/update notifications to stdout on disconnect (line 231: {method: session/update, params: {message: 'Connection lost'}}). No sessionId, no update payload — Toad's parser barfs.
- Reconnect delays are too short (10ms * 2^n for 5 attempts = 310ms total). Real server restart takes 1-2+ seconds. Proxy gives up before server returns.
- On give-up, process exits; Toad's stdin closes and it crashes.

Rewrite contract:
- stdout carries only ACP-conformant messages. No raw text or malformed notifications.
- Disconnect emits: session/update with agent_thought_chunk, text 'remote connection lost' (per cached sessionId).
- Reconnect emits: session/update with agent_thought_chunk, text 'reconnected to remote'.
- Request arriving during disconnect: JSON-RPC error response with code -32099, message 'remote connection lost, reconnecting'.
- Reconnect never gives up; new capped backoff (base acp.proxy-reconnect-delay-ms, cap acp.proxy-reconnect-max-delay-ms; prod defaults TBD but reasonable — e.g. 1000ms base, 5000ms cap).
- Per-attempt log event :acp-proxy/reconnect-attempt with :attempt counter (for observability + test sync).
- :acp-proxy/gave-up event must NEVER fire — implementation has no code path that emits it.

Spec: features/acp/reconnect.feature

Introduces test steps:
- 'N loopback reconnect attempts have failed' — synchronizes on :acp-proxy/reconnect-attempt log events
- 'the acp proxy is still running' — checks proxy future not done

Drops existing 'gives up after max reconnect attempts' scenario (behavior removed).

Acceptance:
1. Remove @wip from every scenario in features/acp/reconnect.feature
2. bb features features/acp/reconnect.feature passes
3. bb features and bb spec pass

## Notes

Completed with bb spec green and features/acp/reconnect.feature green. Full bb features still has unrelated failures outside this bead's scope; see isaac-f88c.

