---
# isaac-3mtu
title: sessions cancel is a silent no-op for sessions under a crew directory — cancel writes the legacy marker path
status: todo
type: bug
priority: high
tags:
    - agent
    - sessions
created_at: 2026-09-21T18:31:37Z
updated_at: 2026-09-21T18:31:37Z
---

Repo: **isaac-agent** (`src/isaac/session/store/impl_common.clj`,
`src/isaac/session/cli.clj`). Field 2026-09-21 18:28Z: Micah ran
`isaac sessions cancel <id>` for four sessions on zanebot. Exit 0, no
output, and nothing happened — the turns kept running.

## Why

`request-cancel!*` resolves the marker with the two-arity
`turn-marker-path root session-id`, the crew-less legacy location
`sessions/<id>/turn.edn`. Since isaac-b6w0 live markers live at
`sessions/<crew>/<id>/turn.edn`. `exists?*` is false, the `when` returns
nil, `run-cancel` returns 0 and prints nothing. Recording a marker already
uses the crew-aware `turn-marker-path-for` / `locate-session`; cancel was
missed in that migration. (`clear-turn-marker!*` next to it deserves the
same look.)

## Fix

- `request-cancel!*` resolves the marker through `turn-marker-path-for`
  (crew-aware, legacy fallback) exactly like record/clear.
- `sessions cancel` reports what it did: `cancelled <id>` on success; on a
  missing marker it already says "no turn is in progress" — but that branch
  reads through the store (crew-aware) while the write did not, which is how
  a session could be reported in progress and still not be cancelled. After
  the fix both paths agree. A no-op write is an error exit, never a silent 0.

## Scenarios (`isaac-agent/features/session/cli.feature` or the cancel feature)

- `sessions cancel <id>` on a session under a crew directory stamps
  `:cancelled true` on `sessions/<crew>/<id>/turn.edn` and the running turn
  ends `:cancelled`
- the command prints `cancelled <id>` and exits 0; a session with no marker
  exits 1 with the existing message

Noticed while recovering the isaac-3wiu misbind after the 18:24Z restart.
