---
# isaac-iskp
title: 'isaac remote: reconnect through a server restart instead of exiting'
status: todo
type: feature
priority: high
created_at: 2026-09-05T04:42:28Z
updated_at: 2026-09-05T04:43:27Z
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
| Given the stub server on reattach replays frames: | existing |
| **Given the stub server refuses reattach for {n:int} attempts** | **NEW** — loopback transport stays dropped for n connects (ws.clj `ReconnectableTransport` gains a refuse counter) |
| **Given the stub server drops the connection permanently** | **NEW** — wraps existing `drop-loopback-permanently!` |
| **Given the stub server answers reattach with an unknown-stream error** | **NEW** — reattach reply = error frame `unknown stream s-1` |
| **Given the stdin lines are:** (table) | **NEW** in this module (feeds the stdin pump before run) |
| When isaac remote is run with {args} | existing |
| Then the stub server received frames: | existing (row order asserts the second start / replayed handshake) |
| Then the stdout contains / does not contain | existing |
| Then the stderr contains | existing |
| Then the exit code is N | existing |
| Then the reconnect window is {n:int} seconds (env override) | folded into scenario 1 via `ISAAC_REMOTE_RECONNECT_SECS=3` set by the run step — no new step; use `isaac remote is run with` after `Given the env var … is …` from foundation cli-steps |

## Acceptance
- [ ] 4 scenarios above green with @wip removed; existing 8 remote scenarios unchanged
- [ ] `bb features && bb spec` green in isaac-cli-proxy
- [ ] Manual: restart zanebot's server while a Toad ACP session is open → session survives (stderr shows reconnect attempts, then `reattached` or `restarted`) and the next prompt answers
- [ ] Module version bump + registry pin (train step)
- [ ] Planner note: reconnect defaults recorded in the module README



Feature planted: isaac-cli-proxy main c1ee5ad (4 @wip scenarios). One step beyond the ledger is new: **Then the stdout contains {text} exactly once** — the existing `does not contain` step cannot express "once, not twice" for a JSON id. Also **Given the env var {name} is {value}** is assumed to exist in foundation cli-steps; if it does not, it is a NEW step in this module.
