---
# isaac-79t1
title: 'Foreman :output :event: a turn''s last reply line names its event; Foreman lists the valid events in the preamble'
status: todo
type: feature
created_at: 2026-10-05T01:29:09Z
updated_at: 2026-10-05T01:29:09Z
blocked_by:
    - isaac-8j0t
---

Likely repo: **isaac-foreman**. Micah + planner, 2026-10-04. Blocked by isaac-8j0t (turn outcomes: :output, one outcome per turn). Treat as an experiment: log every parse.

## Why

A model should not need to know Foreman's tool to move a machine. With
`:output :event`, the turn's reply says what happened, and Foreman reads it.
A missing or garbled line is safe: the turn is quiet, Foreman reports
`:foreman/turn-ended`, and a generic row can alert someone (Micah: "a safe
fallback for a model messing up the output format").

## Design (agreed)

- `:output :event` on a `:turn`: Foreman reads the reply's **last non-blank
  line** as `event: <name>` or `event: <name> <reason>`, case-insensitive on
  `event:`. `<name>` is signaled as the turn's one outcome, source `:reply`
  (history shows "via reply"); `<reason>` goes in the event's data as `:reason`.
- The valid events are the outgoing transitions of the state the instance just
  entered, minus `:foreman/*` and `:*`-only rows' duplicates. A name not on the
  list, or no event line, is a quiet turn → `:foreman/turn-ended`.
- Foreman writes the instructions into the turn's preamble: that the turn is
  part of machine X / instance Y, and to end the reply with one line
  `event: <name>` (listing the valid names), with an optional short reason.
- Log `:foreman/reply-event` with `:parsed` (name or nil), `:valid?`, `:machine`,
  `:instance`, `:action`, so a miss rate is visible. If misses show up, the
  structured alternative is a turn-scoped tool whose `event` is an enum.
- `:output :event` on `:exec` works the same on the command's stdout (no
  preamble).

## Acceptance

- isaac-foreman `features/foreman/reply_event.feature` — all five scenarios.
- The rest of the isaac-foreman features stay green.

feature-baseline: isaac-foreman 1f0c559fd56dcc4abd6b28e07b047e816bbe63a9
feature-blob: isaac-foreman features/foreman/reply_event.feature deb92a326eea2767086a14a28624692fb82d2c51
