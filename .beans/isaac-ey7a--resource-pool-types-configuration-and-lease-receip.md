---
# isaac-ey7a
title: 'Resource pools replace turnstiles: types, named instances, busy-means-wait'
status: in-progress
type: feature
priority: normal
created_at: 2026-09-27T22:33:11Z
updated_at: 2026-09-28T02:07:56Z
parent: isaac-q3u3
---

Likely repo: **isaac-agent** (plus a mechanical rename touch in isaac-worksite's pin-bump, owned by isaac-npmp). Design: Micah + planner, 2026-09-27.

## Resource pools replace turnstiles (clean cutover)

Decision (2026-09-27, Micah): Agent's turnstiles already do all-or-nothing admission, give-back on a hold, reverse-order release, and wake-on-release. "Turnstile" was a pun (one turn through at a time) and confusing; the natural name for what we're building is **resource pool**. Pools replace turnstiles — one admission mechanism, no aliases, no `turnstile` name left in code, config, CLI, or features.

- Berth `:isaac.agent/turnstiles` → `:isaac.agent/resource-pool-types`. Modules contribute a type factory plus a type-specific config schema; registration does not create a lease.
- **Pools are named config instances** under `:resource-pools` (entity dir `config/resource-pools/<name>.edn` and root form), each with a `:type`. Per-request refs with params (`--turnstile tide:22:00-06:00`) are removed. A turn names instances: CLI `--pool <name>` (repeatable), charge `:resource-pools [...]`. Unknown types, unknown names, and invalid type config fail validation; a turn naming an unknown pool refuses before dispatch.
- Protocol `Turnstile (admit? / release!)` → `ResourcePool (try-acquire / release!)`. **Busy means wait**: `try-acquire` answers a lease or `:busy`; there is no busy-refusal (Worksite's `:worksite-busy` refusal goes away with isaac-npmp).
- **Acquisition never blocks** (contract, not enforced by timeout): `try-acquire` returns promptly and never waits on a lock, connection, or network. Admission serves every session; one blocking acquire would stall them all.
- **Order:** acquire in the order the turn lists its pools; on any `:busy`, release what was taken (reverse order) and the request stays held. Release on turn end runs in reverse order and wakes the queue (existing wake hook).
- **Tide stays as a built-in pool type**: `{:type :tide :window "22:00-06:00"}` — available only inside its window. It is the only exercise of the clock-tick wake path.
- `turns list` shows a `resource-pools` column.
- Receipts (bindings, `:session/cwd`) and restart release are **isaac-i5lv**; tool-call leases are isaac-kxqj.


## Acceptance

Features committed `@wip` on isaac-agent main at 0cf9824. Remove every `@wip`; all pass:

- [ ] `bb features features/turn/resource_pools.feature` — new file, 4 scenarios (`:19` validation, `:39` unknown pool, `:51` busy second pool gives back the first, `:75` tide window)
- [ ] `bb features features/turn/turn_queue.feature` — 5 existing scenarios rewritten for pools (`:27`, `:53`, `:66`, `:84`, `:111`)
- [ ] New/renamed steps in `spec/isaac/turn/queue_steps.clj` (and `session_steps.clj`): `a scripted resource pool "<name>" admits <n> turn(s) at a time` (registers a scripted type AND writes the named instance into config), `resource pool "<name>" is closed|opened`, `the user sends "…" on session "…" with resource pools "…"`. The old turnstile steps are deleted.
- [ ] One-time check: `git grep -i turnstile` in isaac-agent returns nothing (src, spec, features, manifests, marigold). `features/turn/turnstiles.feature` is already deleted.
- [ ] `bb verify` green; version bump.

Knock-on: isaac-worksite's `:worksite` turnstile stops compiling when it repins to this Agent — converting it is isaac-npmp's job; do not touch worksite here.

feature-baseline: isaac-agent 0cf98241c81f37b75f39661985d6ab4494e835e1
feature-blob: isaac-agent features/turn/resource_pools.feature 9b36d5e4817a48d5700026cfd6412f56e7720e55
feature-blob: isaac-agent features/turn/turn_queue.feature 32f594eef0e186626285d53765b5d3be66857fd1


## Worker checkpoint (2026-09-27)

Done: implemented named pool types/instances, validation, CLI --pool, busy rollback and wake path; resource_pools.feature passes (4/4). Commit 3d0fd21 pushed on bean/isaac-ey7a.
Next: turn_queue.feature remains RED (3 transcript assertions after wake at :44, :59, :74; :84 can hang). Inspect wake-config at src/isaac/turn/worker.clj:26 and feature config installation in spec/isaac/turn/queue_steps.clj:65; then run bb features features/turn/turn_queue.feature:27. Remaining spec cutover and bb verify/gate pending.
