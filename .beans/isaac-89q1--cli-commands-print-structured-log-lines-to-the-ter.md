---
# isaac-89q1
title: 'CLI commands print structured log lines to the terminal: the CLI log sink is configured after config loads'
status: todo
type: bug
priority: high
created_at: 2026-09-24T13:37:58Z
updated_at: 2026-09-24T13:37:58Z
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
