---
# isaac-uaxj
title: A lease whose owner process is dead lists as free
status: todo
type: bug
created_at: 2026-10-07T14:12:58Z
updated_at: 2026-10-07T14:12:58Z
---

Likely repo: **isaac-worksite**. Found after Foreman pilot 1, 2026-10-06.

## Why

`worksites list` showed work-2..4 as `leased (bean-isaac-8uno)` for hours after their
owner (pid 28583) died. A lease whose owner process is dead is already free in
practice — the next acquire takes it over (`steal-stale-turn!`) — so listing it as
leased only misleads.

## Design (Micah, 2026-10-07)

- A stale lease is a free member. `lock/lock-state` reports a turn lock whose owner is
  dead (`stale-turn-lock?`) as `:free`, so `list` and acquire agree. No "stale" label,
  no new unlock path; `unlock` stays operator-only.
- The scenario first asserts the planted lock really exists where the CLI reads it
  (new step `worksite "<member>" has a turn lock from pid <n>`): the planted-stale
  step may write somewhere `list`/acquire don't look — a draft of this scenario passed
  before any fix. If so, fix the step (and check whether the existing "a dead
  process's lease is stolen" scenario is vacuous for the same reason).

## Acceptance

- isaac-worksite `features/worksite/lock.feature:167`.
- The rest of isaac-worksite features stay green.

feature-baseline: isaac-worksite 1564db8a4026c44c910a0b1742c69db419986105
feature-blob: isaac-worksite features/worksite/lock.feature 8787d9aecb04f40cb642064077e7c60b66c8039a 167
