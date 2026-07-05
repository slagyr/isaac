---
# isaac-6a2f
title: Discord gateway does not self-recover from a crashed reader loop (wtg8 watchdog ineffective)
status: draft
type: bug
priority: high
created_at: 2026-07-05T16:16:48Z
updated_at: 2026-07-05T16:16:48Z
---

## Problem

The Discord gateway still crashes and does NOT self-recover, despite isaac-wtg8 (watchdog + reader hardening + reconnect idempotency) being deployed (discord 0.1.8). When the reader loop dies, the gateway stays down until a manual service restart — taking notifications AND (via the wedge) other subsystems with it.

## Evidence (2026-07-05, zanebot, discord 0.1.8 deployed)

- `discord.gateway/reader-loop-failed` (java.io.IOException "Output closed") at 10:54:58.
- Next `discord.gateway/ready` not until 16:14 (a manual restart) — ~5h with no self-recovery.
- ZERO `watchdog` events in the logs — the liveness watchdog (wtg8 start-liveness-watchdog!) is not visibly firing/logging/recovering.
- This has now missed multiple times (overnight 2026-07-03 pre-wtg8; again 2026-07-05 post-wtg8).

## Likely gaps (investigate)

- The liveness watchdog either is not scheduled, not detecting staleness, or its forced-reconnect path is not actually re-establishing the socket.
- The reader-error -> on-close! -> reconnect path may not fire for this specific IOException, or the reconnect fails silently.
- No health/observability: a dead gateway is invisible until someone notices Discord is quiet. Needs a health signal (ties to isaac-royn supervision) and loud logging on crash + each recovery attempt.

## Desired behavior

- A crashed/stale gateway reader is detected within a bounded time and the socket is re-established automatically (verified by a gateway/ready after a reader-loop-failed with no manual restart).
- Every crash, watchdog tick, reconnect attempt, and recovery is logged at INFO/WARN so recovery is observable.
- A persistently-unrecoverable gateway is surfaced via health (service status / royn supervision), not silent.

## Scope

isaac-discord (gateway.clj watchdog + reconnect; service.clj liveness). Coordinate with isaac-royn (subsystem supervision + health surface). Add an integration test that kills the reader and asserts auto-recovery.

Priority: HIGH — repeated real outages; a gateway crash wedges the whole pipeline.
