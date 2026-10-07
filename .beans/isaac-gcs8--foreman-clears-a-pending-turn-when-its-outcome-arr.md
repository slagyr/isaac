---
# isaac-gcs8
title: Foreman clears a pending turn when its outcome arrives; turn-started is history, not unhandled
status: todo
type: bug
created_at: 2026-10-07T17:57:50Z
updated_at: 2026-10-07T17:57:50Z
---

Likely repo: **isaac-foreman**. Found by Foreman pilots 1 and 2, 2026-10-06/07. Design approved by Micah 2026-10-07.

## Why

1. **A finished turn stays pending.** After isaac-htix landed, `foreman status` still
   showed `pending: work (turn) submitted c506172d`. Pending actions are replaced only
   when a transition queues new ones (`core.clj` ~167:
   `(when (seq pending) (store/set-pending! …))`); `working → landed` queues nothing,
   and nothing removes a pending turn when its outcome arrives.
2. **turn-started reads as an error.** Every Foreman turn reports
   `:foreman/turn-started`; machines have no row for it, so each turn writes an
   `unhandled` record into the history and prints `unhandled: foreman/turn-started`
   to the server's stderr.

## Design

- A pending `:turn` entry leaves `:pending-actions` when ITS turn reports an outcome —
  a reply event, `turn-ended`, `turn-failed` or `turn-died` for that `request-id`.
  Not on every transition (a turn still running when someone signals `abandon` stays
  tracked). A refused submission (no request id, `:error`) stays for `retry`.
- `turn-started` with no matching row is history, not unhandled: a history line
  `<action> turn started <request-id>` (action looked up from the pending entry by
  request id; just `turn started <request-id>` for a turn Foreman didn't submit), no
  stderr line. A machine WITH a `turn-started` row still transitions on it.
- `foreman status` therefore shows the turn under history once it starts, and the
  pending line only while it is genuinely outstanding.

## Acceptance

Re-cuts of existing scenarios (now @wip):
- isaac-foreman `features/foreman/turn_action.feature:35` — signaled turn: no pending
  line after `lit`; `tend-lamp turn started <id>` in history.
- isaac-foreman `features/foreman/turn_action.feature:92` — backstop turn after retry:
  no pending line after `unlit`.
- isaac-foreman `features/foreman/events.feature:114` — `turn started <id>` history, no
  `unhandled`.
- The rest of isaac-foreman features stay green.

feature-baseline: isaac-foreman 8437a365c4ff8309f66da9e1aadcea17545697d6
feature-blob: isaac-foreman features/foreman/turn_action.feature d90c3e3e47ff8a69c755e978d3e726ffbd88977e 35,92
feature-blob: isaac-foreman features/foreman/events.feature da1d0d768d1c19453a2b21cd77898d000e3c76f5 114
