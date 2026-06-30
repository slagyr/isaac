---
# isaac-f0fq
title: Berth log :output (file/stderr/stdout/none) in the foundation :logging config
status: completed
type: feature
priority: normal
tags: []
created_at: 2026-06-29T23:59:42Z
updated_at: 2026-06-30T00:09:52Z
---

The foundation :logging config berth (isaac-foundation manifest) currently exposes only :max-bytes / :max-days. The log OUTPUT destination is set programmatically (logger/set-output!), not via config, and :stdout isn't a supported value at all (logger.clj save-entry has cases for :memory/:stderr/:none + a file default — no :stdout branch). Surfaced 2026-06-29 while reviewing the tqm1/k9b7 logging work.

## Scope
- Add :output to the foundation :logging schema: keyword, one of :file | :stderr | :stdout | :none (default :file). (Optionally :file <path> too, though server.log path is handled by the rotation module.)
- Add the missing :stdout branch to logger.clj save-entry: (:stdout (println (pr-str entry))) -> *out*.
- Drive the sink from config at startup (read :logging.output) instead of hard-coded set-output! defaults; keep sane per-process defaults (server -> :file/server.log, CLI -> :file/cli.log per k9b7).

## Why
- Makes logging destination configurable via isaac.edn rather than code.
- Enables the 12-factor 'B' model (server logs to :stdout, supervisor/launchd captures + rotates) without code changes — currently impossible because :stdout silently falls through to the file default.

## Related
isaac-tqm1 (server log lifecycle), isaac-k9b7 (CLI -> cli.log). Defaults unchanged; this is additive config exposure.

## Verification (2026-06-29)
Verified on fetched GitHub `isaac-foundation` `main` `4c70173fc978d4168c47c61bf68ee1bdfc12a2a2`.

The delivered surface matches the bean:

- `:logging.output` is declared in the foundation config schema
- `logger.clj` now has a real `:stdout` branch
- startup sink selection is factored through `isaac.log.output`
- CLI/server defaults remain `:file`-based while config can switch to `:stderr`, `:stdout`, or `:none`

Focused proof passed:

- `bb spec spec/isaac/log_output_spec.clj spec/isaac/logger_spec.clj spec/isaac/main_spec.clj spec/isaac/config/schema_spec.clj` -> `73 examples, 0 failures, 122 assertions`
