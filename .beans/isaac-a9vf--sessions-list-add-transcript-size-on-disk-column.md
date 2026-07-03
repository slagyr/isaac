---
# isaac-a9vf
title: Sessions list: add transcript size-on-disk column
status: draft
type: feature
priority: normal
created_at: 2026-07-03T06:20:00Z
updated_at: 2026-07-03T06:20:00Z
blocked_by: []
---

## Problem

`isaac sessions list` currently shows age, last-input token usage, context
window, percent of window, crew, and tags. It does not show how large the
session transcript is on disk.

That makes it hard to distinguish:

- a session whose prompt accounting is large but whose retained transcript is
  still modest
- a session whose JSONL transcript has grown large on disk and may need
  operator attention

On zanebot, recent sessions like:

- `orchestration-plan` -> `452K`
- `isaac-verify` -> `792K`
- `orchestration-verify` -> `1.6M`

were easy to inspect with `du`, but that information is absent from the normal
operator view.

## Desired behavior

Add a size-on-disk column to `isaac sessions list`, sourced from the backing
transcript file for each session.

This should be an operator-facing metric, separate from token accounting.

## Likely repo scope

- `isaac-agent`
  - `src/isaac/session/cli.clj`
- session store helpers if the CLI needs a cleaner way to resolve transcript
  paths

## Notes

- The column should reflect transcript file size on disk, not cumulative token
  counters.
- Keep formatting compact and scan-friendly (`452K`, `1.6M`, etc.).
- This likely belongs next to the current context/token columns, but exact
  layout can be decided during implementation.

## Acceptance

Scenarios deferred. Promote to `todo` once the display shape and verification
approach are pinned.
