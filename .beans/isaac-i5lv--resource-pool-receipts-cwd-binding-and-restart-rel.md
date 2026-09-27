---
# isaac-i5lv
title: 'Resource pool receipts: cwd binding and restart release'
status: draft
type: feature
created_at: 2026-09-27T23:45:55Z
updated_at: 2026-09-27T23:45:55Z
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
