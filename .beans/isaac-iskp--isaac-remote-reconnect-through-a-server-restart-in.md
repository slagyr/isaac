---
# isaac-iskp
title: 'isaac remote: reconnect through a server restart instead of exiting'
status: completed
type: feature
priority: high
created_at: 2026-09-05T04:42:28Z
updated_at: 2026-09-05T19:27:53Z
---

Repo: isaac-cli-proxy (feature file: features/remote.feature). Toad/ACP and every `isaac remote` client die with exit 1 whenever zanebot's server restarts, because the proxy's reattach window is <0.4 s and there is nothing to reattach to after a restart.

## Problem
`proxy.clj`: `*reconnect-delays-ms* [25 50 100 200]` — four attempts, under half a second total; a restart takes 30–60 s. Reattach sends an attach frame with the old stream-id; `isaac-cli-server` keeps stream state in an in-memory atom, so after a restart the attach fails (unknown stream) and the proxy exits 1. Toad shows "Agent failed to run"; the user's ACP session is lost (2026-09-05 04:25Z).

## Decisions (ruled by Micah 2026-09-05)
1. **Reconnect window**: exponential backoff (0.25 s, 0.5, 1, 2, 4, then 5 s cap) until a 120 s total; `ISAAC_REMOTE_RECONNECT_SECS` overrides the total; one stderr status line per attempt (`isaac remote: reconnecting (attempt N)…`). Status never goes to stdout.
2. **Fresh start when the stream is gone**: if the server answers the attach with an `error` frame whose message is unknown-stream (or the stream-id is absent), send a new `start` frame with the same argv/cwd/stdout-tty; stateless commands simply rerun.
3. **Handshake replay for ACP** (proxy-side, opt-in): when `argv` begins with `acp`, the proxy records the last `initialize` and the last `session/new`/`session/load` JSON-RPC lines forwarded from stdin; after a fresh start it replays them (initialize, then `session/load` with the sessionId the client is using) and drops their responses so the client never sees duplicates. A `session/prompt` that was in flight at the drop gets a synthesized `{"stopReason":"end_turn"}` response so the client does not hang (the server resumes the paused turn on boot; its output reaches the reattached session on the next prompt).

## Scenarios (planted @wip in features/remote.feature)
- the proxy keeps reconnecting through a 30 s outage
- the proxy gives up after the reconnect window
- an unknown stream after a server restart starts the command fresh
- an acp remote replays initialize and session/load once after a fresh start

## Step ledger
| Step | Status |
|---|---|
| Given a stub /cli server that assigns stream-id … and replies with frames: | existing |
| Given the stub server drops the connection after sending | existing |
| Given the stub server on reattach replays frames: | existing — **fixture extension**: accepts a `message` column so an `error` row can carry `unknown stream s-1`; rows AFTER an error row are the stub's replies to the next `start` frame (fresh start) |
| **Given the stub server refuses reattach for {n:int} attempts** | **NEW** — the loopback transport rejects the first n connects, then accepts; with n beyond the window it also covers the give-up scenario (no separate "permanently" step) |
| Given the env var "X" is set to "Y" | existing (foundation config-steps; require or re-register in this module) |
| And stdin is: (docstring) | existing |
| When isaac remote is run with {args} | existing |
| Then the stub server received frames: | existing (row order asserts attach → second start → replayed handshake; `data` column takes `#"regex"`) |
| Then the stdout contains / does not contain | existing — duplicate-swallowing is asserted by marking the fresh-start replies `"replayed":true` and requiring stdout does not contain it |
| Then the stderr contains | existing |
| Then the exit code is N | existing |

## Acceptance
- [ ] 4 scenarios above green with @wip removed; existing 8 remote scenarios unchanged
- [ ] `bb features && bb spec` green in isaac-cli-proxy
- [ ] Manual: restart zanebot's server while a Toad ACP session is open → session survives (stderr shows reconnect attempts, then `reattached` or `restarted`) and the next prompt answers
- [ ] Module version bump + registry pin (train step)
- [ ] Planner note: reconnect defaults recorded in the module README

Feature planted: isaac-cli-proxy main 58b583f (4 @wip scenarios; `bb features` → 7 existing green, @wip skipped). Exactly ONE new step (refuses reattach for n attempts) plus two fixture extensions to the existing reattach-replay step.

## Handoff

branch: bean/isaac-iskp @ 3de25b446805f2c5985eab9db64e3cd27dc4db27 (base origin/main@58b583f2444e94f79a68656b33e1ae251f9aa5d5)

Implemented reconnect window (0.25s, 0.5, 1, 2, 4, then 5s cap until 120s; ISAAC_REMOTE_RECONNECT_SECS overrides), stderr-only `isaac remote: reconnecting (attempt N)…`, unknown-stream → fresh start, ACP initialize + session/load replay with swallowed duplicates. README records reconnect defaults. Module version bump + registry pin left to train. Manual Toad ACP restart is not a worker gate.

`bb spec` 17/0, `bb features` 11/0 (4 new + 7 existing remote scenarios).

## Landed on main (2026-09-05)

main-sha: isaac-cli-proxy 3de25b446805f2c5985eab9db64e3cd27dc4db27



## Train (2026-09-05)
Registry pinned isaac.cli-proxy → 3de25b4 (0.1.3). The proxy runs on the CLIENT (Toad's / the operator's isaac install), not on zanebot, so deployment = `isaac modules upgrade` on each client machine (dev-local checkouts: `git pull`). Manual acceptance (restart zanebot while a Toad ACP session is open → session survives) is Micah's to run after upgrading their client.
