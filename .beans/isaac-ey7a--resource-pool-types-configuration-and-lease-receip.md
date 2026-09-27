---
# isaac-ey7a
title: 'Resource pools replace turnstiles: types, named instances, busy-means-wait'
status: draft
type: feature
priority: normal
created_at: 2026-09-27T22:33:11Z
updated_at: 2026-09-27T23:46:08Z
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
