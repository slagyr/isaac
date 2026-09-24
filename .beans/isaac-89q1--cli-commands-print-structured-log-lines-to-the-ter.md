---
# isaac-89q1
title: CLI commands never print log entries; config warnings are shown only by the config commands, as warnings
status: todo
type: bug
priority: high
created_at: 2026-09-24T13:37:58Z
updated_at: 2026-09-24T13:48:22Z
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
