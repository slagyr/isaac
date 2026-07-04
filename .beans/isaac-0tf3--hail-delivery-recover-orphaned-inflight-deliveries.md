---
# isaac-0tf3
title: 'Hail delivery: recover orphaned inflight deliveries after a mid-drive crash'
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-07-04T14:35:52Z
updated_at: 2026-07-04T14:41:00Z
---

## Problem

Hail delivery has NO crash recovery. If the delivery worker is killed mid-drive, the claimed hail is orphaned in `hail/inflight/` forever — no retry, no dead-letter.

## Evidence (2026-07-04, zanebot overnight)

Four hails (incl. isaac-895i's `00511001`) sat in `inflight/` at `:attempts 0` for ~9 hours. Nothing progressed; the bean board froze at 9 remaining all night.

## Mechanism

`claim-delivery!` (delivery_worker.clj ~216) moves a delivery `deliveries/ -> inflight/` BEFORE driving the turn. Success -> `finish-delivered!` (deletes inflight); failure -> `reschedule!` (increments `:attempts`, re-queues to `deliveries/`). `:attempts` increments ONLY on the failure path. A hail in inflight at attempts 0 = claimed, turn-drive started, but never completed — the worker died mid-drive, bypassing its own error handler. The main loop only re-scans `deliveries/` (`list-deliveries` ~359), NEVER `inflight/`, so orphaned records are stuck permanently.

## Desired behavior

On worker startup (and/or a periodic sweep), orphaned `inflight/` records older than a threshold are recovered: re-queued to `deliveries/` (attempts preserved or incremented) so they retry, and dead-letter normally if they keep failing. A single mid-drive crash must never strand a hail permanently.

## Scope

isaac-hail: `src/isaac/hail/delivery_worker.clj` (startup recovery of inflight/ + optional sweep). Consider incrementing `:attempts` at claim time so a crash-loop still dead-letters after N.

## Proposed acceptance (gherkin, isaac-hail)

- Given a hail orphaned in inflight/ (attempts 0), when the delivery worker starts, then it is re-queued to deliveries/ and delivered.
- Given an inflight hail that has been orphaned across N worker starts, then it dead-letters rather than retrying forever.

Priority: HIGH — silent permanent loss of queued work.

## Resolution (unverified — for verifier)

- `recover-orphaned-inflight!` sweeps `hail/inflight/` on every tick and at `start!`.
- Orphan = `:claimed-at` older than threshold (default 5m; override via `:inflight-recovery-ms` or `:hail-settings :inflight-recovery-ms`).
- Recovery clears session in-flight, calls `reschedule!` with `:worker-crash` (increments attempts; dead-letters at 5).
- `claim-delivery!` now stamps `:claimed-at`.
- `delivery.feature`: 2 scenarios (recover+deliver; dead-letter at attempts 4).
- `bb ci` green in isaac-hail.
