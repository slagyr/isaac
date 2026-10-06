---
# isaac-tais
title: turns drop tells the turn's observers it died, so a dropped Foreman turn stalls its machine
status: in-progress
type: bug
priority: normal
created_at: 2026-10-06T20:23:24Z
updated_at: 2026-10-06T20:46:58Z
---

Likely repos: **isaac-agent** (drop) + **isaac-foreman** (scenario). Found by Foreman pilot 1, 2026-10-06.

## Why

`isaac turns drop <id>` only rewrites the queue record (`turn/cli.clj` `run-drop`:
`{:state :finished :outcome :dropped}`). The turn's observers never hear about it. A dropped
Foreman turn leaves its instance in `working` forever with `pending: work (turn) submitted <id>`;
that is where pilot 1's isaac-8uno instance is parked now.

## Design

- Drop resolves the record's `:observers` (same registry and refs as submission) and calls
  `on-turn-died` with reason `dropped`. The turn never started, so no `on-turn-started`.
- Foreman's observer already maps died to `:foreman/turn-died`; the machine's own transitions
  decide what happens (bean-work stalls and alerts).
- Feature harness: Agent's `isaac is run with` postflight captures `#turn-id` from
  `held:`/`queued:` output; also capture it from `submitted <id>` (what `foreman status` prints).

## Acceptance

- isaac-agent `features/turn/turn_queue.feature:124`.
- isaac-foreman `features/foreman/turn_outcomes.feature:73`.
- The rest of both repos' features stay green.

feature-baseline: isaac-agent 45b19b48ccb55fd91fe609654907c8f818555b65
feature-baseline: isaac-foreman b2572e6e9b0ed2fd2719c5c0e92fc594f256a5a2
feature-blob: isaac-agent features/turn/turn_queue.feature 757fdb2aae303b734de91b60031d36358940cb9b 124
feature-blob: isaac-foreman features/foreman/turn_outcomes.feature a149d861d865ef76b97b418bb8b587940ee7b974 73
