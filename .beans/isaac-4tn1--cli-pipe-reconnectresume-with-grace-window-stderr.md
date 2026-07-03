---
# isaac-4tn1
title: 'cli pipe: reconnect/resume with grace window + stderr status lines'
status: draft
type: feature
priority: normal
created_at: 2026-07-03T15:34:23Z
updated_at: 2026-07-03T15:35:22Z
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
