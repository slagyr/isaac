---
# isaac-qvhy
title: 'cli-server: embedded dispatch — hosted commands run on a server thread, not a subprocess'
status: draft
type: feature
priority: high
tags:
    - cli
created_at: 2026-09-17T15:55:24Z
updated_at: 2026-09-17T15:55:24Z
parent: isaac-eqkb
blocked_by:
    - isaac-dq4v
---

Child 3 of isaac-eqkb. Blocked by the host-library bean (child 1); commands light up as child 2 migrates them.

## Design (isaac-cli-server `dispatch.clj`)

- `start` → run `host/run-embedded` on a worker thread per stream instead of `p/process`. stdout/stderr = line-framing writers that call the existing `route-frame!`; stdin frames feed a piped reader; `stdin-close` closes it; `stdout-tty` → host `tty?`.
- Stream table, `start-ack`, buffering, attach/replay, grace window: UNCHANGED, but `:proc` becomes `:task`. Grace expiry / destroy ⇒ `host` cancel + thread interrupt (not `Process.destroy`).
- Exit frame from `run-embedded`'s return code.
- `:local-only` and mismatched-`--root` refusals surface as stderr + `exit 2` (from child 1).
- Per-command wall-clock timeout (config `:cli-server :timeout-ms`, default none for stdin-holding commands; hot-reloadable).
- **Transitional only:** a command whose module has not adopted the host (no marker from child 2 — e.g. manifest `:isaac/cli` entry lacks `:hosted true`) still spawns the subprocess. No client-visible switch. Child 5 deletes this path and the marker.
- PROTOCOL.md (both repos, lockstep): rewrite "Execution model" prose only; frames unchanged.

## Acceptance (features/cli/endpoint.feature — scenarios to be written @wip at promotion, with step ledger)

1. a hosted command streams stdout before exit (duplex echo command, replaces the `cat` stub scenario).
2. a command calling exit with code 3 yields `exit 3`, and the server serves a subsequent command (replaces the `sh -c "exit 3"` containment scenario).
3. a throwing command yields stderr + `exit 1`; server still serves.
4. the server's log level/output and command registry are unchanged after a command runs.
5. a `:local-only` command is refused with exit 2.
6. grace window: detached hosted stream keeps running and replays; expiry cancels it (existing scenarios, re-pointed at tasks).
7. an un-hosted command still runs via subprocess (transitional; removed by child 5).

```
cd isaac-cli-server && bb features features/cli && bb ci
cd isaac-cli-proxy && bb features-slow features/integration.feature
```
zanebot after the train: `time isaac remote -- sessions list` vs the pre-change number, recorded in the bean.
