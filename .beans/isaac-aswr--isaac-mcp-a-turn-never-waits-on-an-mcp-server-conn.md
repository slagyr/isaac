---
# isaac-aswr
title: 'isaac-mcp: a turn never waits on an MCP server — connect in the background, hold failures with backoff'
status: in-progress
type: bug
priority: high
created_at: 2026-09-25T02:00:22Z
updated_at: 2026-09-25T02:01:48Z
---

## Symptom

On yopp every turn waited 30 s before `drive/turn-accepted` — the 👀 reaction
and the reply both came ~50 s after the message. Four turns on 2026-09-24/25,
four `mcp/connect-failed :server :linear "MCP initialize failed: timeout"`
lines, each stamped the same second the turn was accepted. The Linear MCP
was misconfigured (header expanded empty → OAuth dance → hang), but the
runtime turned one broken server into a per-turn tax on every reply.

## Cause

`isaac.mcp.runtime/ensure-server!` is the `:isaac.agent/tool-providers`
entry. The agent's tool registry calls it while resolving a turn's tool
context (`isaac.tool.registry/tool-providers`, called for any crew whose
`:allow` names the server prefix — yopp allows `:linear/*`). When no live
client exists it calls `connect-server!` **synchronously**: spawn +
`initialize` + `tools/list`, bounded only by `client/DEFAULT-TIMEOUT-MS`
(30 s). `RETRY-HOLD-MS` (60 s, isaac-vadd) only helps when turns arrive
within a minute of the last failure; yopp's turns were minutes apart, so
every one re-paid the full timeout. `start!` (boot and every `:mcp` config
reload) has the same synchronous shape.

Micah's ruling (2026-09-25): **a turn never waits on an MCP server** — not
on spawn, not on initialize, not on a timeout.

## Design

- `ensure-server!` becomes non-blocking. It returns the tool names already
  registered for that server (or nil) immediately. If there is no live
  client, the server is not held, and no connect is in flight, it starts
  the connect on a background thread and returns nil for this turn. The
  tools land in the registry when the connect finishes and are part of the
  next turn's prompt (same path `recatalog!` already uses).
- One connect in flight per server (an atom slot); a second `ensure-server!`
  while it runs returns nil without spawning again.
- Failure hold with backoff instead of a flat 60 s: 60 s, 2 min, 4 min …
  capped at 15 min; reset on success. Log `:mcp/connect-failed` on each
  failure as today, plus `:mcp/connect-held` once per hold period (not per
  turn).
- `start!` (boot / reload) kicks off the same background connects and
  returns without waiting. Boot and config reload must not block on a
  dead server either.
- `call-server!` keeps its reconnect-on-demand for a registered tool whose
  process died (inside a tool call, bounded by the server's timeout) — out
  of scope here.
- No new config keys. `:timeout-ms` per server keeps its meaning for
  requests.

## Acceptance (isaac-mcp spec)

- [ ] A server whose command never answers `initialize`: `ensure-server!`
  returns within 100 ms on the first call and on every call while the
  connect is pending; exactly one process is spawned.
- [ ] When the background connect succeeds, the next `ensure-server!`
  returns the registered tool names and the tools resolve through the
  registry; no turn observed the connect latency.
- [ ] When it fails, the server is held; a call during the hold spawns
  nothing and returns nil; the hold doubles per consecutive failure up to
  the cap and resets after a success.
- [ ] `start!` with one dead server and one live server returns before the
  dead one's timeout; the live server's tools register.
- [ ] `stop!` cancels/abandons an in-flight connect without throwing and
  leaves no registered tools behind.
- [ ] Log events: `:mcp/connect-failed` per failure, `:mcp/connect-held`
  once per hold, `:mcp/connected` on success (existing).
- [ ] Version bump, manifest untouched, `bb spec` and `bb lint` green; the
  agent-side provider contract (`isaac.tool.registry/tool-providers`)
  needs no change — confirm by reading, not by editing.

Likely repo scope: isaac-mcp (runtime.clj, spec). Read-only in isaac-agent
(tool/registry.clj) for the provider contract.
