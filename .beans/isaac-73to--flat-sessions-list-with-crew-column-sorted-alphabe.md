---
# isaac-73to
title: Flat sessions list with crew column, sorted alphabetically
status: todo
type: feature
created_at: 2026-06-03T08:56:24Z
updated_at: 2026-06-03T08:56:24Z
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
