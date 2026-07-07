---
# isaac-iouj
title: 'cli-server logs remote command executions: command, timing, exit code'
status: completed
type: feature
priority: normal
created_at: 2026-07-07T18:23:49Z
updated_at: 2026-07-07T19:44:41Z
---


## Gap

The /cli endpoint (isaac-cli-server) executes arbitrary isaac commands sent over the websocket by remote clients, but leaves no audit trail: nothing records WHICH commands ran, WHEN, or HOW they exited. With the remote pipe now a first-class surface (isaac remote / ACP over /cli, live since the lcay chain), operators have no way to answer "what has this endpoint been asked to do?" without packet-level archaeology.

## Behavior

The cli handler logs each remote command execution:
- On start: INFO `:cli/command-started` — the command argv (as received, post `--` handling), stream id, and timestamp.
- On completion: INFO `:cli/command-finished` — same correlation ids, the exit code, and duration-ms.
- Abnormal endings (subprocess killed by grace-window expiry, stream abandoned) log the same finished event with the real exit/reason — no silent endings (see isaac-axzg lineage: states that stop moving must make noise).

Consistent with the hail lifecycle logging precedent (isaac-jnkp): grep `:cli/` in the server log reconstructs the endpoint's history chronologically.

## Acceptance sketch (spec to confirm)

Scenario shape (isaac-cli-server features): a remote command runs to completion → log has entries matching `:cli/command-started` (command argv) and `:cli/command-finished` (exit 0, duration present); a failing command logs its non-zero exit.

## Scenario (approved 2026-07-07)

Committed @wip: isaac-cli-server `features/cli/endpoint.feature` line 94 (commit 5548045). No real execution — new stub variant `a recording spawn stub that exits with code N` (records the spawn, simulates completion); everything else reuses endpoint.feature machinery. The non-zero exit proves capture, not hardcoding.

## Acceptance

- [x] `bb features features/cli/endpoint.feature:94` green (isaac-cli-server)
- [x] Abnormal endings (grace-window kill, abandoned stream) also emit :cli/command-finished with the real exit/reason
- [x] Full suite green; @wip removed

## Work Notes

- Implemented remote CLI execution lifecycle logging in `isaac-cli-server` on branch `isaac-iouj-cli-command-logging`.
- `src/isaac/cli_server/dispatch.clj` now logs `:cli/command-started` with argv and stream-id, and `:cli/command-finished` with stream-id, duration, and either exit code or abnormal reason.
- Detached-stream exits now log `:reason :abandoned-stream` with the real exit code when the subprocess finishes after disconnect.
- Grace-window expiry now logs `:reason :grace-window-expired` before destroy so the kill path is audible instead of silent.
- Added dispatch specs for normal completion, detached completion, and grace-window expiry.
- Removed `@wip` from `features/cli/endpoint.feature` and added feature support so cli log assertions run against in-memory logging.
- Verified with `bb spec`, `bb features features/cli/endpoint.feature`, full `bb features`, and full `bb ci`.
