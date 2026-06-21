---
# isaac-6zll
title: service subcommand --help discards the generated option summary
status: in-progress
type: bug
priority: normal
created_at: 2026-06-21T23:07:23Z
updated_at: 2026-06-21T23:09:17Z
---

`isaac service install --help` (and `isaac service logs --help`) print only a hardcoded usage line and never list their options, so flags like `--runtime`, `--root`, `--isaac-bin`, `--bb-bin`, `--isaac-dir`, `--follow` are invisible in help even though they work.

## Cause
In `isaac-server/src/isaac/service/cli.clj`:
- `run-install` (~line 48): the `(:help options)` branch is `(do (println "Usage: isaac service install [options]") 0)` — it discards `tools-cli/parse-opts`'s `:summary` (the formatted, aligned option list).
- `run-logs` (~line 129): has no `--help` branch at all; `-h/--help` falls through and the command just runs.

## Fix
- install help branch: also print `(:summary (tools-cli/parse-opts (:_raw-args opts) install-options))`.
- logs: add a `(:help options)` branch that prints usage + `(:summary ...)` for `logs-options`.
- Consider a small shared helper so future subcommands render option help consistently.

## Acceptance
- `isaac service install --help` lists every flag in `install-options` (incl. `--runtime ... bb (default) or jvm`) with descriptions.
- `isaac service logs --help` lists `--follow` and exits 0 without running the command.

## Notes
Surfaced 2026-06-21 while probing the JVM-runtime cutover: the blind help made it look like fxbp's `--runtime` flag wasn't shipped, when it was (`isaac service status` reports `runtime: bb`).
