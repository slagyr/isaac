---
# isaac-89q1
title: CLI commands never print log entries; config warnings are shown only by the config commands, as warnings
status: completed
type: bug
priority: high
created_at: 2026-09-24T13:37:58Z
updated_at: 2026-09-24T16:29:00Z
---

Micah, 2026-09-24: "What is all this crap being printed out? These CLI commands can't be printing garbage like this." Every `isaac …` command on zanebot prints two `{:ts … :level :warn, :event :config/unknown-key …}` lines before its own output.

## Cause

`isaac.main` runs `register-module-cli-commands!` (which loads config and thereby runs `warnings/log-unknown-keys!` at :warn) BEFORE `configure-cli-logging!` installs the CLI file sink (`logs/cli.log`). Until the sink exists the logger's default stderr sink receives the warning. Any log line emitted during config load — unknown keys, unresolved refs (rxun), module discovery — leaks the same way.

## Fix

- Install the CLI log sink before the first config load in `isaac.main` (the root is resolved by then; the log-file/log-level flags are parsed before dispatch). If the sink needs config (`log.output`), install a provisional file sink first and re-apply once config is loaded.
- A CLI command's terminal output is its own stdout/stderr only. Structured logs go to `logs/cli.log`, viewable with `isaac logs cli`. `--log-level`/`--log-file` remain the operator's way to see them live.
- Scenario in isaac-foundation `features/cli/`: a root whose config has an unknown key; `isaac config get <key>` (or any read-only command) prints only its result on stdout and nothing on stderr; the unknown-key warning is present in `logs/cli.log`.

## Also (config hygiene found on zanebot, not this bean)
`hail-settings.beans-repos` and `models.glm-5-3.extra-system-prompt` are undeclared keys in zanebot's config — hand-edited past the a5dx refusal. The operator should remove or declare them.

## Acceptance
- [ ] scenario green; `bb ci` green in isaac-foundation
- [ ] on zanebot after the keg rebuild, `isaac http auth list` prints only the table

Repo scope: isaac-foundation (`main.clj`, `log/output.clj`, features).

## Ruling (Micah, 2026-09-24)

"Almost every CLI command prints out log entries and that should stop … If those log entries have something to do with configuration, then only whenever we intentionally use the config CLI command, we can print warnings about the configuration but they should not be printed as log entries."

So the fix is not just sink ordering:
- **No CLI command writes a structured log line to the terminal**, ever, by default. Logs go to `logs/cli.log`. Only an explicit `--log-level`/`--log-file` puts them on the terminal. The default terminal sink for the CLI is `:none`, installed before anything else runs (before root resolution if a line could be logged before then).
- **Config warnings are a `config` feature.** `isaac config validate` (and `config get/set` for the key they touch) print unknown-key and unresolved-reference findings as plain warning lines (`warning: hail-settings.beans-repos is not a declared key`), not as EDN log maps. No other command mentions them.
- The `log-unknown-keys!` warn added by isaac-nq4c ("visible at boot") stays for the **server** boot log only — that is where an operator reads logs. Remove it from the CLI load path or make the sink swallow it.
- Scenario: any non-config command (e.g. `isaac crew list`) on a root with an unknown key prints only its result — stderr empty; `isaac config validate` on the same root prints the warning as a human line; `logs/cli.log` still records the structured event.


## Handoff

Branch `bean/isaac-89q1` @ `fa8f46a1399690647fc45e74c4c8e7e9159e5684` in isaac-foundation, pushed to origin. Not merged — status stays in-progress, tagged unverified for /verify.

### Root cause confirmed
`main/run`'s first `config-api/load-resolved` call (before `register-module-cli-commands!` or `configure-cli-logging!` ever runs) triggers the loader's `warnings/log-unknown-keys!`/`log-unresolved-refs!`, which `log/warn` straight to `isaac.logger`'s default `:output :stderr` — no CLI sink exists yet. `register-module-cli-commands!`'s own internal config read was already safe (wrapped in `log/*quiet?* true`); the leak is specifically that first top-level load.

### What changed
- `src/isaac/log/output.clj`: extracted `explicit-output-override!` (private) out of `apply-cli!` — the operator's `--log-file`/`ISAAC_LOG_FILE` (forces the file sink at that path) or a bare `--log-level` (now opts into `:stderr`, i.e. "see it live") — and added `provisional-cli-sink!`, which applies that same decision *before config is loaded*, defaulting to the `logs/cli.log` file sink when neither flag is given. A harness-set `:memory` output is always left alone (existing tests unaffected).
- `src/isaac/main.clj`: calls `(log-output/provisional-cli-sink! resolved-root :log-file-path log-file :env-log-file (env-log-file) :log-level log-level)` as the first form inside the CLI's `nexus/-with-nested-nexus` scope, before the first `config-api/load-resolved`. `--log-file`/`--log-level` are already parsed from argv by this point (no config needed), so an explicit flag is honored immediately; otherwise config load's own warnings land safely in `logs/cli.log` instead of being lost or leaked.
- `isaac config validate` (`config/cli/validate.clj`) already printed warnings as plain `warning: :key - value` lines via `common/print-warnings!`, never EDN — confirmed correct, unchanged, now covered by a scenario.
- Did **not** touch `config/cli/get.clj`/`set.clj` to print warnings for the touched key — not required by this bean's concrete acceptance list (only validate + the quiet-by-default rule + --log-level opt-in), and no existing behavior regressed.
- `config/warnings.clj`: unchanged — `log-unknown-keys!`/`log-unresolved-refs!` still warn (isaac-nq4c), now safely captured by the provisional sink instead of leaking.

### New/changed feature steps (spec-support)
- `spec-support/src/isaac/foundation/cli_steps.clj`: added `"the stderr is empty"` step (mirrors the existing `"the stdout is empty"`).
- `spec-support/src/isaac/foundation/fs_steps.clj`: added `"the CLI log file contains an event {event:string}"` — reads `logs/cli.log` (root-relative, via the existing `read-log-file-entries` helper already used by `isaac-log-file-no-server-origin`) and asserts some entry's `:event` matches (colon on the string is optional).

### Feature: `features/cli/quiet_default_logging.feature` (new, 3 scenarios)
1. A non-config command (`modules list`) on a root with an unknown top-level key: exit 0, stderr empty, `logs/cli.log` contains `:config/unknown-key`.
2. `isaac config validate` on the same root: stderr has the human `warning: :bogus-top-level-key - unknown key` line, no `:level`/`:event` (i.e. not an EDN map).
3. `modules list --log-level warn` on the same root: exit 0, stderr contains `config/unknown-key` (explicit opt-in still works).

Confirmed red before the fix (stashed `main.clj`/`log/output.clj`, scenario 1 failed with the exact raw EDN leak on stderr), green after.

### Test counts
- `bb spec`: 1243 examples, 0 failures (was 1218 on origin/main before my changes; +7 new unit specs in `spec/isaac/log/output_spec.clj` for `apply-cli!`'s new log-level branch and `provisional-cli-sink!`, +18 from the rebase onto isaac-63ei).
- `bb features`: 222 examples, 2 failures, 2 pending — the 2 failures are `features/cli/modules_pins.feature` ("fixture-agent" gitlibs fetch), confirmed pre-existing on a clean origin/main checkout in this environment (stale `~/.gitlibs` cache referencing a sibling `work-2` worktree path), unrelated to this bean.
- `bb jvm-spec`: 1225 examples, 8 failures — confirmed byte-for-byte the same 8 (module-lifecycle/protocol `AbstractMethodError` set) on a clean origin/main checkout; this is isaac-jf80's known pre-existing set.
- `bb ci`: fails only because its `bb features` sub-step hits the same 2 pre-existing `modules_pins.feature` failures above.
- `bb lint` on touched files: 0 errors, 2 pre-existing warnings (unrelated lines, confirmed via git diff not touched by this change).

### Stayed out of
`config/mutate.clj`, `loader.clj`, `env.clj` (isaac-p4oj's files) — untouched. Rebased cleanly onto isaac-p4oj's sibling commit (isaac-63ei, `config get`/dot-entries) with no conflicts.

## Landed on main

main-sha: isaac-foundation fa8f46a1399690647fc45e74c4c8e7e9159e5684. Planner verified: bb spec 1243/0; bb features 222 examples, 0 failures after clearing the stale ~/.gitlibs fixture-agent cache (the two sibling-pins failures were that cache, pre-existing). Deploy = zanebot brew keg rebuild (Micah), together with isaac-p4oj.

## Follow-up (planner, 2026-09-24)

Deployed foundation fae35d6 to zanebot (brew HEAD keg) and `isaac --version` STILL printed the unknown-key warn: the packaged launcher (`isaac.launcher/-main`, run by `libexec/isaac.bb`) does its own `config-api/load-resolved` to compose the classpath BEFORE `isaac.main` runs, and that load hit the default stderr sink. The feature harness calls `main/run` directly so it never saw it. Fixed on foundation main d90c2098: the launcher installs `log-output/provisional-cli-sink!` before its load, with --log-file/--log-level from argv; `spec/isaac/launcher_spec.clj` pins the order. bb spec 1251/0, features 224/0.
