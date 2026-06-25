---
# isaac-fmc2
title: sessions subcommands mishandle --help (set/unset/show/delete treat --help as a positional arg)
status: in-progress
type: bug
priority: normal
tags:
    - unverified
created_at: 2026-06-25T18:05:37Z
updated_at: 2026-06-25T18:25:59Z
---

`isaac sessions set --help` prints `invalid path: --help` instead of showing help. The sessions subcommands pass positional args straight through without checking for `--help`/`-h` first. Mirror the crew management-command pattern (isaac-3d8j) which handles per-subcommand help correctly.

## Diagnosis (isaac-agent src/isaac/session/cli.clj, run-fn ~384)
Each subcommand takes a positional arg without a help check:
- `set`:    `(run-mutation opts :set (second raw-args) (nth raw-args 2 nil))` -> `set --help` -> path = "--help" -> `parse-mutation-target` -> "invalid path: --help".
- `unset`:  `(run-mutation opts :unset (second raw-args) nil)` -> same.
- `delete`: `(run-delete opts (second raw-args))` -> `delete --help` would target session "--help".
- `show`:   parses options from `(drop 2 raw-args)` and uses `(second raw-args)` as the id -> `show --help` treats "--help" as a session id, not help.

## Fix — mirror crew (isaac-3d8j)
crew's `show` (crew/cli.clj): `(parse-with-arguments (rest raw-args))` -> if `(:help options)` -> `(print-show-help!)` BEFORE using the positional. Apply the same to each sessions subcommand:
- Parse the subcommand's args for options + positionals (a parse-with-arguments equivalent; sessions currently only has parse-option-map).
- If `--help`/`-h` present -> print that subcommand's help text and return 0.
- Otherwise proceed with the positional (id/path/value).
- Add per-subcommand help text (set/unset/show/delete), like crew's `print-show-help!`/`show-help-text`. set/unset help should show the `<id>.<path> <value>` usage.

## Acceptance
- `isaac sessions set --help` / `unset --help` / `show --help` / `delete --help` each print that subcommand's help and exit 0 (no "invalid path"/no accidental mutation/delete).
- The positional paths/ids still work when --help is absent.
- Feature coverage in the sessions cli feature (mirror the crew show-help scenarios from 3d8j).

## Related
- isaac-3d8j (crew management command + per-subcommand help) — the pattern to copy.
- isaac-q6xu (sessions shows help by default; list subcommand) — recent sessions mgmt work; this fills the per-subcommand-help gap it left.
- isaac-hzp1 (sessions/crew --help lists subcommands) — top-level help; distinct from this (subcommand-level --help).

## Notes
Surfaced 2026-06-25 on zanebot: `sessions set --help` -> `invalid path: --help`. Micah: 'similar to the recent crew bean, this should work on sessions'.

## Scenarios (approved 2026-06-25, Micah) — written @wip in isaac-agent features/session/cli.feature
4 scenarios (individual, NOT a Scenario Outline — the <id>/<path>/<value> in the usage strings collide with Gherkin outline placeholder syntax):
- sessions set --help    -> stdout contains "Usage: isaac sessions set <id>.<path> <value>", exit 0
- sessions unset --help  -> stdout contains "Usage: isaac sessions unset <id>.<path>", exit 0
- sessions show --help   -> stdout contains "Usage: isaac sessions show <id>", exit 0
- sessions delete --help -> stdout contains "Usage: isaac sessions delete <id>", exit 0
Positive assertions (what it SHOULD show), per Micah — not 'does not contain invalid path'. Reuse existing steps (isaac is run with, the stdout contains, the exit code is). Mirrors crew 'crew show --help' (3d8j). The exact Usage strings are the contract the implementer must produce. DoD: implement per-subcommand --help handling, remove @wip, scenarios green.

## Worker notes (work-1, 2026-06-25)

isaac-agent @ `fd559dc`.

- `src/isaac/session/cli.clj`: added `help-requested?` (--help/-h scan) + per-subcommand help-text (`subcommand-help-text`/`print-subcommand-help!` + set/unset/show/delete usage consts). `run-fn` short-circuits to that subcommand's help (exit 0) before touching positionals, for set/unset/show/delete.
- Chose a --help scan over crew's parse-with-arguments so `set` values that start with `-` (e.g. negative numbers) still pass through positionally (parse-opts would reject them). Same per-subcommand-help contract as crew (3d8j), safer for the free value arg.
- Exact Usage strings produced: 'Usage: isaac sessions set <id>.<path> <value>' / 'unset <id>.<path>' / 'show <id>' / 'delete <id>'.
- Un-wip'd the 4 sessions <sub> --help scenarios in features/session/cli.feature (left the unrelated in-flight @wip scenarios alone).
- Tests: `clojure -M:features features/session/cli.feature` 17/0; `bb ci` spec 1077/0, features 540/0.
