---
# isaac-gfsq
title: isaac server never binds the server.log sink (configure-server-sink! not called on boot)
status: completed
type: bug
priority: high
tags: []
created_at: 2026-06-30T01:45:03Z
updated_at: 2026-06-30T01:49:29Z
---

The server does NOT write <root>/logs/server.log on origin/main — confirmed by code, not just the stale zanebot deploy. tqm1's "verified" was a false pass: the specs exercise the rotation logic by calling configure-server-sink! directly, but the real `isaac server` boot never calls it.

## Evidence (origin/main)
- log/file.clj configure-server-sink! (binds <root>/logs/server.log): the ONLY callers are spec/isaac/log_file_spec.clj and logger_spec.clj. NO src caller.
- server/cli.clj `run`: the only sink config is `(when logs (... set-log-file! ; set-output! :file))` — gated behind the `--logs` flag, and it points at the tail path, not the rotating server sink.
- logger.clj save-entry routes to the rotating server log only `(if (lfile/server-sink?) ...)`; server-sink? is set by configure-server-sink!, which never runs -> always false in production.
- `isaac server` is dispatched as a CLI command -> main.clj configure-cli-logging! binds the CLI sink (cli.log on origin/main, stderr on 0.1.13). So server logs land in cli.log / stderr, never server.log.

## Impact
- The core goal of tqm1 (durable server log at ~/.isaac/logs/server.log) is NOT met in production.
- Deploying 0.1.14 would not fix server logging.

## Fix
- Call configure-server-sink! during `isaac server` startup (unconditional — not gated by --logs), with the resolved root + :logging config, so the server binds the rotating <root>/logs/server.log sink.
- It must take precedence over the CLI-dispatch sink (configure-cli-logging! runs first when `isaac server` is dispatched).
- Add an acceptance that actually boots `isaac server` (not just calls configure-server-sink! in a spec) and asserts <root>/logs/server.log is written.

## Related
isaac-tqm1 (verification gap), isaac-k9b7 (cli.log), isaac-f0fq (:output berth).

## Verification (2026-06-30)
Verified on fetched GitHub `isaac-server` `main` `83706a06d2ad18e8ec367ea6e61785ce699b500d`, where the `gfsq` closeout commit is present on top of the earlier logging stack.

Proofs were green:

- `bb spec spec/isaac/server/logging_spec.clj spec/isaac/server/cli_spec.clj` -> `21 examples, 0 failures, 43 assertions`
- `bb features features/server/log_lifecycle.feature` -> `7 examples, 0 failures, 13 assertions`

That covers both the sink binding itself and the real `isaac server` CLI-dispatch acceptance path that was missing from the earlier tqm1 verification.
