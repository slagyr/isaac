---
# isaac-at5m
title: 'Hail: bound deliveries sit unclaimed in deliveries/ forever; no operator drop'
status: in-progress
type: bug
priority: high
created_at: 2026-08-29T14:39:53Z
updated_at: 2026-09-18T06:02:54Z
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



## Scenarios (committed @wip — isaac-hail `features/bound_unclaimed.feature` @ 8dbba29)

| line | scenario |
|------|----------|
| :20 | a gated bound delivery logs why it was skipped on every tick (`:hail/delivery-skipped :reason :session-in-flight :unclaimed-ms`) |
| :40 | a crew at capacity is a named skip reason, not silence (`:crew-at-capacity`) |
| :62 | a bound delivery unclaimed past the stale threshold while its session is idle is claimed with a recovery log (`:hail/delivery-recovered :reason :false-in-flight`) |
| :94 | … while its session is genuinely busy is requeued unbound (`:rebound-stale`, attempts untouched) |
| :122 | the bind timestamp (`:bound-at`) is recorded when the worker binds a delivery |
| :137 | an operator drops a bound-unclaimed delivery (`isaac hail drop <id>` → `undeliverable/` with `:reason :dropped`) and nothing phantom gates the session |
| :170 | hail drop of an unknown id fails cleanly |

Design pinned by the scenarios: `:bound-at` stamped at bind; threshold `:hail :stale-bound-ms` (default 5 min, hot-reloadable); past the threshold the worker re-derives in-flight from the session's durable turn marker (isaac-7li9) rather than the in-memory gate — the 08-29 failure was the gate saying busy while the session was idle ([[isaac-concurrency-model]]); genuinely busy ⇒ unbind and rebind to another idle candidate (attempts untouched — gating is not an attempt). `hail drop` moves the file to `undeliverable/` (already exists as a state) with `:reason :dropped` and clears any bound-session claim.

## Step ledger

| step | status |
|------|--------|
| an Isaac root at … / default Grover setup / the isaac EDN file … exists with: / the following sessions exist: / session … is in flight / the following model responses are queued: / the hail delivery worker ticks at … / the turn ends on session … / the isaac file … EDN contains: / … does not exist / the log has entries matching: / isaac is run with … / the exit code is … / the stdout|stderr contains … / config: | reuse |
| **the in-flight gate falsely reports session {name} busy** | **NEW — marks the session busy in the in-memory gate (`spi/in-flight*`) without a turn marker or running turn; models the 08-29 state** |

One new step.

## Acceptance
```
cd isaac-hail && bb features features/bound_unclaimed.feature features/delivery.feature features/turn-marker-claim.feature && bb ci
```
Version bump; pin is a train step. Field check after the train: `grep delivery-skipped server.log` shows reasons on the next gated tick; `isaac hail drop` on a synthetic stuck delivery on zanebot.
