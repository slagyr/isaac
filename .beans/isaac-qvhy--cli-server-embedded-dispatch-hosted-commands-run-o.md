---
# isaac-qvhy
title: 'cli-server: embedded dispatch — hosted commands run on a server thread, not a subprocess'
status: in-progress
type: feature
priority: high
tags:
    - cli
created_at: 2026-09-17T15:55:24Z
updated_at: 2026-09-18T01:20:51Z
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



## Landed upstream (isaac-dq4v, foundation main 26742d0)
`isaac.cli.host/run-embedded {:argv :root :in :out :err :env :cwd :tty?}` → exit code. Binds `*host*`/`*in*`/`*out*`/`*err*`; `exit!` → `ex-info {:isaac.cli/exit code}` caught inside; throwable → message on err + 1; `:local-only` → exit 2 "run this on the host"; `--root` ≠ server root → exit 2. Cancellation: `(host/cancel! host)` runs the `on-shutdown!` fns and releases `block-until-cancelled!`. The registry passes `:local-only` through (`registry.clj:229-236`) — **this bean adds the transitional `:hosted` key the same way** (one-line foundation leg; pin bump).

Note `run-embedded` builds its own host internally; the dispatcher needs the host handle to cancel it. Either expose a `run-embedded*` that takes a pre-built `embedded-host`, or return it via a callback — worker's call, keep the public `run-embedded` shape.

## Scenarios (committed @wip — isaac-cli-server `features/cli/endpoint.feature` @ ea7327b)

| line | scenario |
|------|----------|
| :114 | a hosted command streams stdin to stdout before it exits |
| :128 | a hosted command that calls exit is contained — the server keeps serving |
| :142 | a hosted command that throws frames the message on stderr and exits 1 |
| :151 | a hosted command leaves the server's process state untouched |
| :161 | a local-only command is refused over the pipe |
| :171 | a --root that is not the server's root is refused |
| :180 | a dropped socket keeps the hosted command alive for the grace window, then cancels it |
| :191 | a reattached client receives frames buffered while detached |
| :202 | a hosted command is logged with argv, timing, and exit code |
| :213 | a command not yet marked hosted still runs as a subprocess (transitional; removed by isaac-dqy9) |

Fixture commands (spec-side, registered into the LIVE registry by the Given, all `:hosted` except `fx-legacy`): `fx-echo` (stdin lines → stdout until EOF), `fx-print <args>`, `fx-exit <n>` (calls `host/exit!`), `fx-throw <msg>`, `fx-block` (`on-shutdown!` records a flag, then `block-until-cancelled!`), `fx-local` (`:local-only`), `fx-legacy` (not hosted).

## Step ledger

| step | status |
|------|--------|
| the cli-server handler with spawn command … / with a recording spawn stub / … and grace window N ms | reuse |
| a /cli client sends start with argv … / sends stdin … / sends stdin-close / disconnects | reuse |
| the grace window elapses | reuse |
| the handler sends frames: | reuse |
| the recorded spawn command is the isaac launcher with args … | reuse |
| the cli log has entries matching: | reuse |
| **the cli-server handler with the fixture commands registered** (+ **… and grace window {n} ms** variant) | **NEW — registers the fx-* commands in the live registry for the scenario, restores after** |
| **no subprocess was spawned** | **NEW — the spawn seam was never called (recording stub with zero calls)** |
| **the server process state is snapshotted** / **the server process state is unchanged** | **NEW — captures identity of the logger state, registry `commands` atom value, config process memo, nexus root-runtime; asserts equal after** |
| **the hosted command is still running** / **is no longer running** | **NEW — the stream's task thread alive / finished** |
| **the hosted command's shutdown fn ran** | **NEW — fx-block's flag** |
| **a /cli client sends attach with the issued stream-id** | **NEW — attach frame using the start-ack stream-id** |

Seven new steps. Scenario :213 combines two existing Givens (fixture registry + recording spawn stub).

## Acceptance
```
cd isaac-cli-server && bb features features/cli/endpoint.feature   # all 10 above green, @wip removed; existing 10 still green
bb spec && bb ci
cd isaac-cli-proxy && bb features-slow features/integration.feature   # remote ACP e2e still green (acp still subprocess until isaac-dqy9)
```
PROTOCOL.md "Execution model" prose updated in both repos, lockstep. Module version bump; registry pin is a train step.

## Worker checkpoint (2026-09-17, scrapper@isaac-work-2)

Done: Foundation `bean/isaac-qvhy` @ `6f1000d` exposes caller-owned `host/run-embedded*`, preserves/declares manifest `:hosted`; cli-server `bean/isaac-qvhy` @ `bb7494f` implements hosted task dispatch, piped stdin/stdout/stderr, exit/error containment, local-only/root refusals, grace cancellation, replay/attach, transitional subprocess fallback, fixture steps, protocol/version/pin updates. cli-proxy `bean/isaac-qvhy` @ `706b193` carries lockstep protocol prose. Green before timeout RED: all endpoint scenarios 20 examples/86 assertions, cli-server specs 10/42, full cli-server CI, proxy slow integration 4/10.

Current state: RED only for newly added timeout spec: expected hosted shutdown callback and exit 124, got no cancellation. Red commit pushed at `bb7494f`. Next: implement hot-reloaded `[:cli-server :timeout-ms]` scheduling/cancel in `src/isaac/cli_server/dispatch.clj:79` / `:220`, make `spec/isaac/cli_server/dispatch_spec.clj:39` green, rerun acceptance, rebase all three branches, update bean and hand off.
