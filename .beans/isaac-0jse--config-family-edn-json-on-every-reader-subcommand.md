---
# isaac-0jse
title: 'config family: --edn/--json on every reader subcommand'
status: todo
type: feature
priority: normal
created_at: 2026-07-12T20:00:04Z
updated_at: 2026-07-12T20:00:04Z
---

## Goal

Every reader subcommand of 'isaac config' accepts --edn and --json and emits structured output (isaac-grof shipped the pattern for keys/list/validate; this extends it family-wide). Observed gap 2026-07-12: 'isaac config get models --json' -> Unknown option.

## Subcommand inventory (from isaac config --help on 0.1.21)

- get [path] — resolved config or subtree. --edn/--json emit the subtree as data. NOTE: get prints VALUES by design (it is the explicit value reader), so no masking — but the structured output must round-trip cleanly (a map in, a map out).
- keys / list / validate — already done (grof); regression-guard only.
- schema [path] — schema node as data.
- sources — contributing config files as a vector/array of entries.
- set / unset — writers; --json/--edn emit a structured result record ({:path ... :ok true :file ...}) per Micah's 'the entire config command should support it'.
- help — exempt (prose).

## Design

- One shared option + rendering seam (grof already built the table/edn/json renderer — reuse it; do not hand-roll per subcommand).
- Table/human output stays the default everywhere; behavior without flags is unchanged.
- Home: isaac-foundation (brew train; batch with other foundation work if any).

## Scenarios (worker writes; required coverage)

1. get with --json on a map path emits parseable JSON equal to the EDN subtree (keywords stringified per the existing convention).
2. get with --edn round-trips: output read back with edn/read-string equals the subtree.
3. schema and sources each emit structured output under both flags.
4. set --json emits a structured result; exit codes unchanged.
5. Regression: keys/list/validate structured output unchanged (grof scenarios stay green).
6. An unknown flag on any subcommand still errors cleanly (no silent swallow).
