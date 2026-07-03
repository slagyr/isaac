---
# isaac-dslv
title: 'Sessions CLI: in-flight visibility (✈️ + filter flags)'
status: completed
type: feature
priority: normal
created_at: 2026-05-23T04:09:25Z
updated_at: 2026-07-03T19:55:11Z
blocked_by:
    - isaac-a1nu
---

## Motivation

The in-flight tracker added by `isaac-a1nu` gives operators a
runtime signal of "what's actually working right now." The
`isaac sessions` listing is the natural surface for that signal.

## Scope

### ✈️ marker

`isaac sessions` renders an ✈️ suffix on the SESSION cell for any
session currently in flight. Idle sessions render unchanged.
Backed by `(in-flight? store sess-id)` reading the in-memory
tracker.

### Filter flags

- `--in-flight` — show only sessions currently in flight.
- `--not-in-flight` — show only idle sessions.
- Mutually exclusive. Passing both exits 1 with a stderr message
  containing "mutually exclusive".

Filters compose with the existing `--crew` filter.

Implementation lives in `src/isaac/session/cli.clj`.

## Acceptance

- The four `@wip` scenarios in `features/session/cli.feature`
  pass with `@wip` removed:
  - sessions output marks in-flight sessions with ✈️
  - sessions --in-flight filters to in-flight sessions only
  - sessions --not-in-flight filters to idle sessions only
  - sessions --in-flight with --not-in-flight is an error
- Run: `bb features features/session/cli.feature`

## Relationship to other beans

- **Blocked by**: `isaac-a1nu` — needs `in-flight?` to be set by
  `bridge/dispatch!` and exposed via the session store.
