---
# isaac-bzlu
title: "Fix WS reader loop conflating poll timeout with close"
status: completed
type: bug
priority: high
created_at: 2026-04-30T18:55:11Z
updated_at: 2026-04-30T22:38:22Z
---

## Description

ws-client's receive-queue-message returns nil on .poll timeout when @closed? is still false. The Discord gateway reader-loop treats that nil as a transport close and synthesizes {:reason "closed"}, killing healthy sessions during the IDENTIFY→READY idle window (~110 ms observed in prod logs across 13 consecutive connects on 2026-04-30).

Root cause: src/isaac/util/ws_client.clj line 27-34 — receive-queue-message falls through to :else message on a timed-out poll, returning nil. gateway.clj line 206 if-let on nil routes to on-close! with the {:reason "closed"} fallback, so the disconnect appears codeless even when Discord never sent a close frame.

Fix: distinguish 'no frame yet' from 'stream closed'. Either return a ::timeout sentinel that the gateway loop recurs on, or have the loop check @closed? itself before calling on-close!.

## Acceptance Criteria

- All 3 scenarios in features/comm/discord/idle.feature pass with @wip removed.
- Existing features/comm/discord/{gateway,reconnect,lifecycle,intake,routing,reply,splitting,typing}.feature still pass.
- ws-client unit specs cover: poll timeout while open returns ::timeout (or equivalent), poll after onClose returns nil with close-payload populated.
- Verify: bb features features/comm/discord/idle.feature && bb spec spec/isaac/util/ws_client_spec.clj spec/isaac/comm/discord/gateway_spec.clj

## Design

Spec: features/comm/discord/idle.feature

Run targeted scenarios:
  bb features features/comm/discord/idle.feature
  bb features features/comm/discord/idle.feature:18
  bb features features/comm/discord/idle.feature:28
  bb features features/comm/discord/idle.feature:35

Files likely touched:
- src/isaac/util/ws_client.clj          (receive-queue-message + protocol)
- src/isaac/comm/discord/gateway.clj    (reader loop + transport-receive!)
- spec/isaac/util/ws_client_spec.clj    (new unit coverage)
- spec/isaac/features/steps/discord.clj (add 'Discord stays silent for N milliseconds' step + extend close step to accept reason)

## Notes

Fixed the WS reader-loop timeout/close conflation. src/isaac/util/ws_client.clj now returns a distinct timeout sentinel when polling while still open; src/isaac/comm/discord/gateway.clj treats that sentinel as 'no frame yet' and keeps the reader loop alive instead of synthesizing a close. Added ws-client unit coverage for poll timeout while open, added Discord idle step phrases ('Discord stays silent for N milliseconds' and close reason text), removed @wip from features/comm/discord/idle.feature, and verified targeted + existing Discord feature sets. Verification: bb features-all features/comm/discord/idle.feature, bb spec spec/isaac/util/ws_client_spec.clj spec/isaac/comm/discord/gateway_spec.clj, bb spec, bb features. Manual smoke on real Discord transport still recommended per bead.

