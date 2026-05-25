---
# isaac-3pu6
title: 'Hail-driven session spawning: get-or-create a crew''s session for spawn-enabled hails'
status: todo
type: feature
priority: normal
created_at: 2026-05-25T01:01:23Z
updated_at: 2026-05-25T02:17:25Z
parent: isaac-ugx7
blocked_by:
    - isaac-7v5h
    - isaac-wte9
---

## Motivation

Hails as **autonomous task dispatch to a dormant crew**: a leak alert to
`:role/engineer`, a "review this PR" to `:role/reviewer`, an incident to
`:role/oncall` — the crew exists but no conversation is live, and you still
want the work done. Without this, such a hail is `undeliverable`.

This bean adds **get-or-create** for spawn-enabled reach-one hails: deliver to
the crew's matching session, **creating it if it doesn't exist**.

## The :spawn flag = descriptive vs prescriptive

`:spawn` is the toggle for how a frequency's tags are read:

- **`:spawn false` (default)** — tags are a read-only **filter** over existing
  sessions (descriptive). No match → `undeliverable`. Nothing is created.
- **`:spawn true`** — **match-or-create** (prescriptive). Existing matching
  session → deliver to it; none → create one under a matching crew, **apply
  the hail's `:session-tags`**, deliver.

Behavior is identical when a matching session exists; the flag only changes
the **no-match** branch. One-liner: **`:spawn` = create the addressed session
if it doesn't exist.**

Opt-in, **default false** — settable on the band and/or the hail frequency
(hail overrides band). Spawning autonomous turns is a real side effect, so it
must be asked for.

## Scope

### Get-or-create key

A session of a crew matching `:crew-tags`/direct-`:crew`, carrying the hail's
`:session-tags`:

- exists & idle → bind, dispatch
- exists & busy → **wait** (never a sibling — preserves the crew's context)
- none → **spawn** under a matching crew, apply `:session-tags`, dispatch

### Constraints

- **reach-one only.** `:reach :all` never spawns.
- **Needs a host crew.** `:session-tags` with no crew constraint and no match
  → `undeliverable` with reason `:no-host`.
- **Capacity-gated.** Don't spawn if the chosen crew is at `:max-in-flight`;
  all matching crews full → wait.
- **Crew pick when several match** — first by crew id among crews with
  capacity headroom.
- **Spawned session** — created via the same path cron uses
  (`session-ctx/create-with-resolved-behavior!`), sequential name, tagged with
  the hail's `:session-tags`, marked `:origin {:kind :hail :hail-id …}` for
  traceability.

### Pipeline placement (touches both 7v5h and wte9)

- **Router (isaac-7v5h):** a spawn-enabled reach-one with a matching crew but
  no session emits an **unbound spawn delivery** (`:crew`/`:session` nil)
  instead of `undeliverable`. No resolvable host crew → `undeliverable`
  `:no-host`. The `:spawn` flag rides on the frequency, already nested under
  `:hail` — no new delivery field.
- **Delivery worker (isaac-wte9):** treats a spawn-enabled delivery as **live
  get-or-create each tick** (re-resolve crews+sessions) rather than frozen
  `:candidates`. Re-checking at delivery time means two spawn-hails to the
  same key don't double-spawn — the second finds the first's session.

## Out of scope (deferred)

- Spawning a **sibling** session when an existing match is merely busy (we
  wait instead — anti-fragmentation).
- Open-ended `:all` / standing subscriptions (separate, explicitly dropped).
- Per-band session-selection policy beyond first-by-id.

## Acceptance

- Spawn-enabled reach-one, matching crew, no session → router emits a spawn
  delivery; worker creates a session (tagged, `:origin :hail`) and dispatches;
  delivery → `delivered/`.
- Existing matching session: idle → bind (no spawn); busy → wait (no sibling).
- Crew at capacity → wait (no spawn).
- `:spawn false` + matching crew, no session → `undeliverable :no-recipients`.
- `:spawn true` + `:session-tags` only, no crew → `undeliverable :no-host`.

## Feature file

`features/hail/spawn.feature` — 7 `@wip` scenarios. Run:

```
bb features features/hail/spawn.feature
```

**Definition of done:** remove `@wip` from `features/hail/spawn.feature` and
`bb features features/hail/spawn.feature` is green.

New reason keyword: `:no-host`. No new step phrases beyond those `7v5h` /
`wte9` already introduce (`the hail router ticks`, `the hail delivery worker
ticks`). Asserting `origin.kind` on a spawned session forces the implementer
to persist `:origin` on the session record.

## Relationship to other beans

- Parent: isaac-ugx7 (Hail epic).
- **Extends isaac-7v5h** (router): adds the spawn-delivery resolution path.
- **Extends isaac-wte9** (delivery worker): adds the get-or-create + spawn
  path. Should land after both 7v5h and wte9 are implemented.
- Uses isaac-a1nu (`in-flight?` / `can-dispatch?`) for idle/capacity.
