---
# isaac-0jse
title: 'config family: --edn/--json on every reader subcommand'
status: completed
type: feature
priority: normal
created_at: 2026-07-12T20:00:04Z
updated_at: 2026-07-12T21:39:39Z
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
7. Help text documents the formats (Micah, 2026-07-12): 'isaac config --help'
   lists --edn/--json in Options, and each supporting subcommand's
   'isaac config help <sub>' mentions them. Scenario: help output contains
   "--json".



## Verify fail (attempt 1, 2026-07-12): acceptance criterion 7 is still unmet — `isaac config --help` does not list `--edn`/`--json` in its Options block

Evidence:
- Bean scenario 7 requires: `isaac config --help` lists `--edn/--json` in Options, and each supporting subcommand help mentions them.
- On implementation branch `origin/bean/isaac-0jse` at `993f6d2`, running `bb isaac --root /tmp/isaac-0jse-verify config --help` prints an Options block containing only `-h, --help`; it does **not** contain `--json` or `--edn`.
- Supporting subcommand help pages do advertise the flags (`config help get/schema/sources/set/unset/validate` all showed `--edn` and `--json`), so the remaining gap is specifically the top-level `config --help` page.
- Code matches the failure: `src/isaac/config/cli/command.clj:16-18` defines top-level `option-spec` as only `["-h" "--help" "Show help"]`, and `config-help` renders Options from that spec.
- Other checks on the bean branch were green: targeted config CLI specs passed (`48 examples, 0 failures, 130 assertions`) and `bb ci` passed (`823 examples, 0 failures, 1450 assertions`; features `131 examples, 0 failures, 329 assertions`).

## Work handoff (2026-07-13, scrapper@isaac-work-1, verify fail fe6afe0f)

Criterion 7 fix already on **`origin/bean/isaac-0jse` @ `e3f554037f3ee79edd72d38cb001cd59def7a99d`** (supersedes fail SHA `993f6d2`):

- `command.clj` top-level `option-spec` = `inspect/structured-option-spec` (`--edn` / `--json` in Options).
- Spec: `command_spec.clj` — "lists --edn and --json in top-level Options (isaac-0jse)".
- Worker gates: `bb spec` 824/0; `bb ci` specs + features green.

**Verify at SHA `e3f554037f3ee79edd72d38cb001cd59def7a99d` only.**
