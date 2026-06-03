---
# isaac-73to
title: Flat sessions list with crew column, sorted alphabetically
status: completed
type: feature
priority: normal
created_at: 2026-06-03T08:56:24Z
updated_at: 2026-06-03T14:42:35Z
---

Replace the crew-grouped session listing with one flat alphabetical
table that carries a CREW column. Removes the `crew: <id>` group
headers from `isaac sessions`.

## Feature

`features/session/cli.feature:17` (the `@wip` scenario:
"sessions defaults to one flat table sorted alphabetically with a
CREW column").

## Acceptance

- Remove `@wip` from `features/session/cli.feature:16`.
- `bb features features/session/cli.feature:17` passes.
- `bb features features/session/cli.feature` (whole file) passes —
  the existing `--crew main` "aligned columns" scenario at line ~57
  still passes (filtered view continues to drop the CREW column).
- `isaac sessions` no longer prints any `crew:` group headers; rows
  are sorted alphabetically by session name; rightmost column is
  CREW.
- `isaac sessions --crew <id>` is unchanged: filters to that crew
  and omits the CREW column.

## Implementation notes

`src/isaac/session/cli.clj`:

- `list-all` currently returns a `{crew-id -> sessions}` map. The
  new shape returns a single sorted-by-name vector. Keep the existing
  return shape behind a private helper if you want the JSON/EDN
  output to stay grouped — or flatten there too (preferred; simpler).
- `print-crew-sessions` is the per-crew renderer; replace with a
  single `print-session-table` that renders one table with the
  CREW column appended.
- `session-columns` (no CREW) is used for `--crew` filtered output;
  add `default-session-columns` with CREW column appended for the
  unfiltered case.
- `tagged-session-columns` already includes CREW + TAGS — preserve
  the "TAGS appear when any session has tags" branch for both code
  paths.

## Summary of Changes

- `src/isaac/session/cli.clj`:
  - New `default-session-columns` (session-columns + CREW), used when no `--crew` filter.
  - `list-all` now returns a single vector of sessions sorted alphabetically by name (was `{crew-id -> sessions-by-date}`). No external callers, so no shim needed.
  - Replaced `print-crew-sessions` with `print-session-table`: one table, picks columns by (any tags? → tagged-session-columns; crew-filter? → session-columns; else → default-session-columns).
  - `run` drops the by-crew grouping for both the text and JSON/EDN paths; sessions are flat, sorted by name.

- `features/session/cli.feature`: removed @wip; the spaced patterns were hand-counted with a 3-space gap but the table renderer (and the already-passing `--crew main` scenario at L57) uses a 2-space gap, so the expected patterns were adjusted to match the actual aligned output.

bb features features/session/cli.feature 11/0; bb spec 1807/0; bb features 726/0.
