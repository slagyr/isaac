---
# isaac-i5lv
title: 'Resource pool receipts: cwd binding and restart release'
status: in-progress
type: feature
priority: normal
created_at: 2026-09-27T23:45:55Z
updated_at: 2026-09-28T04:12:31Z
parent: isaac-q3u3
blocked_by:
    - isaac-ey7a
---

Likely repo: **isaac-agent**. Split out of isaac-ey7a on 2026-09-27 (Micah + planner): ey7a replaces turnstiles with resource pools; this bean adds what a lease *gives* a turn and how leases survive a crash.

## Contract to plan

- A successful `try-acquire` returns a **receipt**: `{:bindings {...} :release-id ...}`. Bindings may be empty (tide).
- `:session/cwd` is the only permitted binding key for now; any other key from a pool is a loud error (a pool bug). It feeds the charge's existing `:cwd` before the charge is built — per turn, never a session property.
- **Restart release.** Each running turn's release ids are written into its session turn marker (the existing durable record for running turns). On restart, an orphaned marker's leases are released. `release!` is idempotent by release id, so no separate reconcile method.
- Live handles are never persisted.

## Scenario plan (to draft)

1. A scripted pool's receipt binds `:session/cwd`; the turn runs there (boot file from that directory is in the prompt).
2. A pool returning an unknown binding key fails the turn loudly and releases what was acquired.
3. A turn marker orphaned by a crash has its lease released on restart; the pool admits the next turn.

Draft until scenarios are committed and baselined.


## Decisions (2026-09-27, Micah + planner)

- Pools are acquired after `charge/build` but before `run-turn!` (`dispatch-matched-charge!`); the drive reads boot files from the charge's `:cwd` when it builds the prompt. So the `:session/cwd` binding is applied to the charge between acquisition and `run-turn!` — before anything reads it; no rebuild.
- The session's stored cwd stays the default; a binding overrides it for that turn only and never writes to the session.
- Each user message transcript entry records the `cwd` its turn ran in.
- The turn marker gains `:leases [{:pool <name> :release-id <id>}]`. On restart, each orphaned marker's leases are released (`release!` is idempotent by release id), then the re-driven turn requests the same pools again.

## Acceptance

Feature: `isaac-agent/features/turn/resource_pool_receipts.feature` (new, 4 scenarios, `@wip` on main at 517388f). Remove `@wip`; all pass:

- [ ] `bb features features/turn/resource_pool_receipts.feature` — `:19` cwd binding per turn, `:48` unknown binding key, `:66` marker records leases, `:79` restart release + re-acquire
- [ ] New steps (in `spec/isaac/turn/queue_steps.clj`): `a scripted resource pool "<name>" binds:` (key/value table; capacity 1; re-running it changes the binding) and `resource pool "<name>" has lease "<id>" out` (the pool is full, held by that release id; releasing that id frees it once).
- [ ] Unit spec: releasing the same release id twice frees the pool once.
- [ ] `bb verify` green; version bump.

feature-baseline: isaac-agent 517388fd82b9a2b6e55791d69a2bd7096b95a6cd
feature-blob: isaac-agent features/turn/resource_pool_receipts.feature 0b3f689ecf00b6eef7af0abdc0c105af9e312fa9
