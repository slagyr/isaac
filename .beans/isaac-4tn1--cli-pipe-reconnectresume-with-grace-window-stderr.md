---
# isaac-4tn1
title: 'cli pipe: reconnect/resume with grace window + stderr status lines'
status: in-progress
type: feature
priority: normal
created_at: 2026-07-03T15:34:23Z
updated_at: 2026-07-06T15:49:28Z
blocked_by:
    - isaac-895i
---

## Context

The old ACP websocket transport had heartbeat + auto-reconnect. The generic /cli pipe has neither: a dropped socket kills the remote command. For long-lived commands (ACP under an editor) that means lost sessions on every network blip.

Decision (2026-07-03, Micah): reconnect lives in the GENERIC pipe — no dedicated acp proxy. Connection-status UX via proxy stderr lines (editors surface agent stderr); optional richer ACP-native status can be an additive wrapper later.

## Design

- Wire protocol addition (PROTOCOL.md, both repos, kept in lockstep):
  - server issues a stream-id on start-ack; on socket drop the server keeps the subprocess alive for a grace window, buffering output frames.
  - client reattaches with {"type":"attach","stream-id":...}; server replays buffered frames and resumes.
  - grace expiry -> process destroyed, buffers dropped.
- Proxy: on drop, keep local stdio open, emit "isaac remote: connection lost, reconnecting..." to stderr, retry with backoff, "reattached" on success, die cleanly after retries exhausted.
- Heartbeat/ping to detect dead sockets promptly.

## Acceptance (scenarios after review)

- Proxy survives a stub-server socket drop and reattaches; replayed frames render once (no dup, no loss).
- Server holds subprocess through grace window; destroys after expiry.
- Status lines appear on proxy stderr, never stdout (stdout is the command stream).

## Likely repo scope

isaac-cli-server + isaac-cli-proxy (+ PROTOCOL.md in lockstep).

## Acceptance scenarios (committed @wip, 2026-07-03)

- isaac-cli-proxy `features/remote.feature` — reattach after drop, render-once replay, status on stderr, attach frame with stream-id.
- isaac-cli-server `features/cli/endpoint.feature` — grace window holds subprocess, expiry destroys (injectable clock, not wall-clock sleeps).

New steps (approved 2026-07-03, full set of 6): stub stream-id Given, scripted drop, scripted replay (proxy); grace-window Given, still-running predicate, grace-elapses clock advance (server).

Interaction: the grace-window scenario SUPERSEDES isaac-895i's unconditional kill-on-disconnect scenario — when this bean lands, that scenario is updated (disconnect → grace, expiry → destroy), not retained alongside.

Acceptance: un-@wip; bb spec / bb features green in BOTH repos; PROTOCOL.md updated in lockstep (stream-id ack, attach frame, grace semantics).
