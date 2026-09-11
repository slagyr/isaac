---
# isaac-at5m
title: 'Hail: bound deliveries sit unclaimed in deliveries/ forever; no operator drop'
status: draft
type: bug
priority: high
created_at: 2026-08-29T14:39:53Z
updated_at: 2026-08-29T14:39:53Z
---

## Problem (2026-08-29, zanebot)

Hail `1164c784` (isaac-6yg0 → isaac-work-1) sat in `~/.isaac/hail/deliveries/` after bind:

- `bound-session` isaac-work-1
- `attempts` 0
- never moved to `inflight/`
- session **not** in-flight (`isaac sessions list --in-flight` empty; work-1 idle, 0% context)
- `hail/inflight/` empty

Router already succeeded. The delivery worker never claimed. A later hail to the same session (`33220619`) became record-only (ledger, no lifecycle file). Human bypass: hail work-2 (`1bdf1ef0`).

This is **not** isaac-0tf3 (orphaned **inflight/** after mid-drive crash). This is **bound-unclaimed in deliveries/** — the tick/claim step never ran, and there is no supported CLI to drop or requeue that file. `isaac hail requeue` (jx7u) is for dead-letters; `isaac turns drop` (ohsy) is for parked turn-queue holds.

## Desired

1. **Worker:** a bound delivery in `deliveries/` must be claimed on a subsequent tick, or skipped with a **loud, greppable** reason (`:hail/delivery-skipped` with why: session-busy, crew-capacity, false in-flight, tick not running, …). Silent parking at attempts 0 is a bug.
2. **Stale bound-unclaimed:** if a bound delivery sits unclaimed longer than a threshold (same order as 0tf3's inflight recovery, ~minutes), recover: log, and either claim, requeue unbound, or dead-letter — never leave it forever.
3. **Operator CLI:** `isaac hail drop <id>` (and/or extend `requeue`) works on **bound-unclaimed deliveries**, not only `failed/`. Dropping must not leave a phantom that gates the session.

## Likely repo

isaac-hail (`delivery_worker.clj`, hail CLI). Related done beans: isaac-0tf3, isaac-wte9, isaac-3tyl, isaac-jx7u, isaac-cehc — do not reopen them; this is the remaining gap.

## Notes

Do not implement from this draft. Promote to todo after scenarios exist (`/plan-with-features`). Observed ids: 1164c784 (stuck deliveries), 33220619 (record-only), 1bdf1ef0 (work-2 bypass).
