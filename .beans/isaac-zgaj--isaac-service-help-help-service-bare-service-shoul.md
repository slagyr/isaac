---
# isaac-zgaj
title: 'isaac service: --help, help service, bare service should all list subcommands'
status: completed
type: bug
priority: normal
created_at: 2026-05-21T04:32:30Z
updated_at: 2026-05-22T04:05:53Z
---

## Motivation

`isaac service` has internal sub-dispatch (install, uninstall, start,
stop, restart, status, logs) but the help paths users instinctively
try all fail:

- `isaac service --help` → "Unknown service subcommand: --help" exit 1
- `isaac help service` → empty `Options:` block (generic
  `command-help` renderer in `src/isaac/cli.clj:31-45` has no info)
- `isaac service` (no args) → one-line `Usage: isaac service <subcommand>`
  with no listing

Same shape will affect any future built-in command that internalizes
sub-dispatch. Discovered while chasing a `service install` permission
error on zanebot — operator had no way to find the right subcommand.

## Scenarios

Three `@wip` scenarios on `features/cli/service.feature`:

- `features/cli/service.feature:113` — `isaac service --help` lists subcommands
- `features/cli/service.feature:130` — `isaac help service` prints same listing
- `features/cli/service.feature:139` — bare `isaac service` prints same listing

Run with:
```
bb features features/cli/service.feature
```

## Approach

`src/isaac/service/cli.clj:93-105` dispatch only matches the 7 named
subcommands. Two-line fix is insufficient: the *content* of the listing
needs to come from somewhere structured. Options:

1. Add `:subcommands [{:name :desc :run}...]` to the registry record;
   `command-help` learns to render it; `dispatch` reads from the same
   list. One source of truth.
2. Keep dispatch as-is but add an explicit help responder for
   `--help`/`-h` and a help-text supplier. Two sources of truth (the
   list in dispatch and the list in the help text).

Lean toward (1) — same shape lets module-contributed commands with
sub-dispatch get the same behavior for free.

## TODOs

- [ ] Decide on subcommand-registry shape (option 1 vs 2).
- [ ] Wire `dispatch` to read from the registered subcommand list.
- [ ] Have `command-help` render the subcommand listing when present.
- [ ] Add a routing for `isaac help <cmd>` to flow through the same
      renderer (it already does, for commands without sub-dispatch).
- [ ] Remove `@wip` tags from the three scenarios.

## Acceptance criteria

- All three `@wip` scenarios in `features/cli/service.feature` pass.
- `isaac service --help` exits 0 with subcommand listing.
- `bb spec` and `bb features` green.

## Notes

Captured 2026-05-20 while operator was trying to install the launchd
service and couldn't discover the subcommand surface.
