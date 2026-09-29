---
# isaac-qrl1
title: 'Foreman: a deliberate signal with no transition is refused; observations stay quiet'
status: in-progress
type: feature
priority: normal
created_at: 2026-09-29T15:47:20Z
updated_at: 2026-09-29T16:17:28Z
parent: isaac-q3u3
blocked_by:
    - isaac-50zy
---

Likely repo: **isaac-foreman**. Decision: Micah + planner, 2026-09-29 — revises the F1 ruling (08-23, isaac-tdgt) that unhandled events are never fatal.

## Why

Today every unhandled event is recorded and warned, and the sender is told OK (CLI exit 0, HTTP 202, tool success). A crew that signals `:landed` while the instance is still `:todo` believes it handed off; the bean sits stuck until someone digs. "Fail" here changes only the answer the sender gets — Foreman does not crash and the instance does not move.

## Contract

- **Deliberate signals** — CLI `foreman signal`, the `foreman__signal` tool, `POST /foreman/events` — with no matching row (explicit or `:*`) are **refused**: message `no transition for <event> from <state>`; CLI exit 1 (stderr), tool error result, HTTP 409. The instance does not change. The refusal is still recorded in history (audit).
- **Observations** from turns (`:turn-started`, `:turn-ended`, `:turn-failed`, `:turn-died`) have no sender waiting; an unhandled observation stays quiet — recorded in history, never an error. Machines stay sparse: write rows only for the events they care about.
- Idempotency is unchanged: a repeated event id returns the original answer.

## Scenario plan (to draft)

1. `foreman signal` with no transition exits 1 with `no transition for earthquake from dark`; the instance stays `dark`; history records it. (Rewrites cli.feature "unhandled events are recorded, never fatal".)
2. A crew's `foreman__signal` with no transition gets a tool error it can read in-turn.
3. `POST /foreman/events` with no transition answers 409 with the same message.
4. A `:*` row still catches the event (not refused).
5. An unhandled turn observation is recorded quietly (existing events.feature coverage stands).

Draft until scenarios exist.


## Acceptance

Scenarios `@wip` on isaac-foreman main at 74b70f5. Remove `@wip` from this bean's scenarios; all pass:

- [ ] `bb features features/foreman/cli.feature:47` — refused with `no transition for earthquake from dark`, exit 1, instance stays `dark`, history records it, a later `dusk` still transitions
- [ ] `bb features features/foreman/events.feature:165` (tool error in-turn) and `:189` (HTTP 409 with the message)
- [ ] Existing coverage stands: `machine.feature:69` (a `:*` row catches — not refused), `events.feature:109` and `:131` (unhandled observations stay quiet)
- [ ] Update `isaac-tdgt`'s F1 note: deliberate signals with no transition are refused (2026-09-29); observations stay lenient.
- [ ] `bb verify` green; version bump.

feature-baseline: isaac-foreman 74b70f54700baf7aeea410dfe0f246b11b021642
feature-blob: isaac-foreman features/foreman/cli.feature 41152f141d71b7d294ae2a482e6db1bb898ad230 47
feature-blob: isaac-foreman features/foreman/events.feature 724768bd4a36490485b399f263959c4bd14c5676 165,189

feature-baseline: isaac-foreman 4767bcef60dddae02f1b30c33aa4613777ee6165
feature-blob: isaac-foreman features/foreman/cli.feature 5c429f591ae6469debb2f7578a11a9399eab92cb 46
feature-blob: isaac-foreman features/foreman/events.feature 724768bd4a36490485b399f263959c4bd14c5676 165,189


(2026-09-29: after isaac-50zy landed, the CLI scenario above is at `cli.feature:46`; the newest baseline lines are in force.)
