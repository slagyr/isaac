---
# isaac-e20m
title: 'Bean Gate: cut over — drain unverified, stop hailing isaac-verify'
status: draft
type: task
priority: high
tags:
    - process
    - beans
created_at: 2026-09-19T20:43:16Z
updated_at: 2026-09-20T04:36:48Z
parent: isaac-rmq6
blocked_by:
    - isaac-cy85
    - isaac-jp4v
    - isaac-przv
    - isaac-4b21
---

Repo: **isaac** (this repo). Child 5 of isaac-rmq6 — the cutover. Blocked by isaac-jp4v, isaac-przv and isaac-4b21.

## Why

With the gate documented (jp4v), the worker landing its own beans (przv) and CI re-checking them (4b21), the dual-run exists only to prove the new path. This bean ends it.

## Preconditions (check, do not assume)

- `beans list --tag=unverified` is empty. Any bean still carrying the tag goes through the **old** path first — do not convert it mid-flight.
- zanebot's `isaac-work` band loads `hail-bean-work-gate`.
- The snitch workflow has run green at least once on a real push.

## Change

1. **AGENTS.md** — the gate path becomes the stated default. The `unverified` + verify-hail path stays documented as what happens to a bean with no `feature-baseline`, which is now the exception rather than the rule. Remove the dual-run paragraph jp4v added; it has served its purpose.
2. **Planner watch rules** — record the new rule in the bean and in AGENTS.md: a gated bean needs no verify hail, so the watch dispatches work and then waits for `completed`; the stall rule still applies to a worker that goes quiet.
3. **Leave alone**: the `isaac-verify` band, the `perceptor` crew, the orchestration process-tests and their `orchestration-*` bands. They are not retired by this bean; a later decision covers them.

## Acceptance — one real bean proves it end to end

Not a document review. The bean is done when a bean with a `feature-baseline` has gone from dispatch to `completed` **without a verify hail**, and the snitch workflow passed on the push that completed it. Record in this bean: the bean id, its `main-sha`, the worker session, and the CI run URL.

If no gated bean is in flight when this one reaches that step, say so and hail the plan band — the planner supplies a small scenario-backed bean for the dogfood rather than the worker inventing one.

Also record what the first real run got wrong, if anything. That list is the whole point of running the dual path first.
