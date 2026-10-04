---
# isaac-7sfq
title: 'Foreman instance data: seeded at start, merged by events, settable by CLI, HTTP and tool, rendered into prompts'
status: completed
type: feature
priority: normal
created_at: 2026-10-04T19:14:19Z
updated_at: 2026-10-04T19:27:53Z
---

Likely repo: **isaac-foreman**. Micah + planner, 2026-10-04. Foundation for the bean-work machine (isaac-q6fj): a machine needs data that outlives one event (bean title/summary, worksite, attempt count).

## Why

Today event data lives only on the event that carried it: `{{data.<key>}}` in a
:turn prompt reads the triggering event's data and nothing earlier. A machine
that is started with context and then moves through several turns (work,
hand-back after a conflict, verify) loses that context after the first event.
The instance record already carries an unused `:context {}` placeholder.

## Design

- The instance record's unused `:context {}` becomes `:data` (clean cutover;
  it is always empty today).
- `foreman start <machine> <id> --data EDN` seeds it (CLI; also the start path
  of HTTP/tool if they gain start).
- On a **handled** transition, the event's data shallow-merges into the
  instance data **before** the transition's actions run; a `nil` value removes
  the key. A refused/unhandled event changes nothing. The event log keeps each
  event's own data as history.
- `{{data.<key>}}` reads the merged instance data; dotted paths reach nested
  maps (`{{data.keeper.name}}`); a missing key fills empty. Existing machines
  that template the triggering event's data keep working (it is merged first).
- Set data without a transition, all with the same merge rule:
  - CLI `foreman data <machine> <id> --set EDN`; `foreman data <machine> <id>` prints it.
  - HTTP `POST /foreman/instances/<machine>/<id>/data` (JSON body, keys keywordized), 200; unknown instance 404.
  - Agent tool `foreman__data` (`foreman/data`): `{machine, instance}` reads; with `set` merges. Granted like any tool.
- `foreman status` shows the data.
- A queued/pending :turn renders its prompt from the data at enqueue time, so
  a later `foreman retry` sends what was meant.
- `resume!` applies unconsumed events with the same merge rule.
- Action results writing into the data comes with the `:exec` action (follow-up bean).

## Acceptance

- isaac-foreman `features/foreman/instance_data.feature` — all six scenarios.
- The rest of the isaac-foreman features (cli, events, machine, machine_tests, turn_action) stay green.

feature-baseline: isaac-foreman b99f376fa554e1a1413b919b25067d613bb96e98
feature-blob: isaac-foreman features/foreman/instance_data.feature d96cef8f7a0859787b17dcd1a1378a54f1879ec7

## Landed on main (2026-10-04)

main-sha: isaac-foreman 31a4566c9726b0c31263ac878bf531283752ab14
