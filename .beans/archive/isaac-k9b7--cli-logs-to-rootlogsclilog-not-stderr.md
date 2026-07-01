---
# isaac-k9b7
title: CLI logs to <root>/logs/cli.log (not stderr)
status: completed
type: feature
priority: normal
tags: []
created_at: 2026-06-29T22:39:06Z
updated_at: 2026-06-29T22:52:25Z
---

Refinement of the CLI-logging decision in isaac-tqm1. tqm1 shipped CLI structured logs defaulting to :stderr (main.clj configure-cli-logging! -> set-output! :stderr when no --log-file). Micah now wants CLI structured logs collected in their own file: <root>/logs/cli.log (e.g. ~/.isaac/logs/cli.log), separate from the server's server.log.

## Scope
- main.clj configure-cli-logging!: default the CLI structured sink to a FILE at <root>/logs/cli.log (create logs/), instead of :stderr.
- Keep --log-file / ISAAC_LOG_FILE override for a custom path.
- Interactive stderr UX (banners, tool calls) stays separate and unaffected.

## Design note (one-writer tension)
Many short-lived CLI processes appending to one cli.log interleaves (violates the one-writer-per-file principle, but contained and separate from server.log). Acceptable for debug-grade CLI logs; if interleaving bites, per-invocation cli-<pid>.log is the alternative. Confirm with Micah.

## Related
isaac-tqm1 (server log lifecycle; server -> server.log). Pairs with it: server.log + cli.log both under <root>/logs/.

## Verification (2026-06-29)
Verified on fetched GitHub `isaac-foundation` `main` `90c29e8efb5e90674c91a86d9daf5ed4ffa1dc7c`, which contains commit `90c29e8` (`Default CLI structured logs to <root>/logs/cli.log`).

Focused proof passed:

- `bb spec spec/isaac/log_file_spec.clj spec/isaac/logger_spec.clj spec/isaac/main_spec.clj` -> `63 examples, 0 failures, 112 assertions`

Current `src/isaac/main.clj` now defaults CLI structured logging to `<root>/logs/cli.log` when neither `--log-file` nor `ISAAC_LOG_FILE` is set, while preserving explicit overrides and leaving interactive stderr UX separate.
