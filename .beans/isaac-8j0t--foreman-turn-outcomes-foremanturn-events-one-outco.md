---
# isaac-8j0t
title: 'Foreman turn outcomes: :foreman/turn-* events, one outcome per turn, one :output key'
status: in-progress
type: feature
priority: normal
created_at: 2026-10-05T01:28:15Z
updated_at: 2026-10-05T01:31:18Z
---

Likely repo: **isaac-foreman**. Micah + planner, 2026-10-04. Part of the Foreman/worksite migration (bean-work machine, isaac-q6fj).

## Why

Designing the bean-work machine surfaced three problems:
- Foreman's turn observer reports turn-started/ended/failed/died for every turn, with
  no namespace (they look like domain events and a model could send them) and on top
  of any signal the turn already sent: a worker that signals `landed` also produces
  `turn-ended`. A planner's late `turn-ended` can then land in the *next* phase and
  trip its backstop row.
- A turn's reply never reaches the instance, so a summarizing turn had to be faked
  with an `:exec` of `isaac prompt`.
- Every prompt had to explain Foreman's mechanics.

## Design (agreed)

- Lifecycle events are `:foreman/turn-started`, `:foreman/turn-ended`,
  `:foreman/turn-failed`, `:foreman/turn-died`, with the action name in their data.
  The `:foreman` namespace is reserved: CLI, HTTP and the foreman__signal tool refuse
  to send it ("reserved").
- One outcome per turn: a turn that sent a signal (signals already carry the turn's
  session/request id) gets no `:foreman/turn-ended`. A turn that sent none gets it.
  `turn-failed` / `turn-died` are always reported.
- One `:output` key on `:turn` and `:exec` says what the output becomes:
  `{:data :<key>}` stores it in the instance data before the turn's outcome is
  applied (so a `:foreman/turn-ended` row's actions can use it). Absent: ignored.
  `:exec`'s `:into` becomes `:output {:data …}` (clean cutover). `:output :event`
  (reply as the next event) is the follow-up bean.
- A turn with `:output {:data :k}` gets Foreman-written preamble text telling the
  model its reply is stored as the instance's `k`, reply with only that content.
- Landed scenarios move to the new names; the two that asserted the double event
  ("…via tool…unhandled: turn-ended") now assert one outcome.

## Acceptance

- isaac-foreman `features/foreman/turn_outcomes.feature` — all three scenarios.
- The @wip scenarios re-cut for the new names in `features/foreman/events.feature`, `features/foreman/turn_action.feature` and `features/foreman/exec_action.feature`.
- The rest of the isaac-foreman features stay green.

feature-baseline: isaac-foreman 0f85655ac08a51a78f90be2a8b8a03ed6bce0675
feature-blob: isaac-foreman features/foreman/turn_outcomes.feature d13fac83f39eb96c1f94ae81a02a16c14567b8ea
feature-blob: isaac-foreman features/foreman/events.feature 7bef66db88b79279dd37d8c56e667124630f84e8
feature-blob: isaac-foreman features/foreman/turn_action.feature f180843b6d21add4270099632365ef261cdb3de6
feature-blob: isaac-foreman features/foreman/exec_action.feature d61d63eda9c5dba8d43ff52658d3e9e2c420d391
