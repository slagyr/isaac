---
# isaac-ec9q
title: 'Remote CLI: run any isaac command over a /cli websocket (client + server)'
status: draft
type: epic
priority: normal
created_at: 2026-06-26T20:58:42Z
updated_at: 2026-06-27T04:06:19Z
blocked_by:
    - isaac-opc4
---

VISION (2026-06-26, with Micah). Generalize the ACP `--remote` proxy into a command-agnostic remote-exec channel so ANY isaac CLI command can run against a remote server. Surfaced while reasoning about ACP session selection: a proxy can only relay, so don't thread selection through a protocol — ship argv + IO to the server and let it run the real CLI. Remote session selection then comes FREE (server parses --crew/--session-tag/--create/--prefer/--with-* locally against its own store).

## Shape
1. isaac-server exposes one route `/cli`, like `/acp` today.
2. Client: `isaac remote https://isaac-host/cli <command...>` — connecting with no command prints the server's usage.
3. Client opens a full-duplex WebSocket to the server.
4. Server runs the main CLI dispatch with the given argv and pipes IO back.
5. Any server-supported command is invokable remotely.

## Hard requirement: ACP from day 1
The channel MUST carry interactive, long-lived, bidirectional streaming sessions (acp, chat) from the start — not just batch. `isaac remote .../cli acp` must drive a remote agent: the editor speaks ACP over local stdio, bytes ride the ws to a server-side `acp` process. This SUBSUMES the bespoke acp proxy; the proxy's hard-won machinery is the model: reconnect/never-give-up (isaac-9rdk), stdin-serialization/ordering (isaac-ob1n), message framing.

## Framing protocol (the genuinely new surface)
A small wire format over ws carrying, with framing + backpressure + ordering:
- argv (+ cwd/root?) on connect
- stdin chunks up; stdout/stderr chunks down (kept separate); final exit code
- full-duplex streaming for interactive commands

## Decisions to settle
- Execution model: in-process (bind *in*/*out*/*err*, call dispatch — light but shares JVM/dynamic-var/concurrency state across callers) vs subprocess per invocation (clean isolation, heavier). Lean subprocess given acp streaming + per-session state.
- cwd/filesystem: commands use cwd (prompt sets session cwd, loads AGENTS.md). Remote cwd is the SERVER's; client may pass --cwd/root. State the semantic change.
- Auth: NON-NEGOTIABLE. `/cli` is remote-shell/RCE-equivalent (server privileges, store, fs). Bearer token like `/acp` at minimum; consider scoping which commands are exposed.

## Proposed children (decompose later)
- remote-cli framing protocol (wire format + IO/exit-code/stream semantics)
- remote-cli-server: `/cli` route on isaac-server, run dispatch, pipe IO, auth
- remote-cli-client: `isaac remote <url>` command, ws, pipe local stdio, usage passthrough
- ACP-over-remote-cli: prove a full interactive ACP session rides the channel
- Deprecate `acp --remote`/`-r`/`-t` in favor of `isaac remote .../cli acp`

## Acceptance
- `isaac remote <url>/cli prompt -m '...'` runs server-side, streams stdout/stderr, returns the real exit code.
- `isaac remote <url>/cli acp` carries a full interactive ACP session (editor drives a remote agent).
- Remote session selection needs ZERO dedicated tests — it's the prompt/sessions CLI run server-side, already covered.
- Auth enforced; unauthenticated rejected.
- Transport tested independently in the remote-cli modules (argv+IO+exit round-trip, streaming, reconnect), command-agnostic.

## Relationship
Rescopes isaac-4e4b's ACP child (isaac-xkc9): ACP-over-the-wire moves HERE; xkc9 shrinks to 'the LOCAL acp command uses the shared selector' (like prompt). chat (isaac-4puj) likewise stays a local selector migration.

## Integration feature design (2026-06-26, reviewed with Micah)

Home: **isaac-cli-proxy** (`features/integration.feature` or similar) — the proxy is the driver (`isaac is run with "remote …"`) and sits at the top of the dep chain. Its test deps include ALL the repos (cli-server, server, agent, foundation), so the classpath is full: no module-import juggling — every module's code is present; the harness just activates the berths it needs (mount `/cli` via the server manifest index like marigold_server; register the `remote` command via cli-registry/register! like acp's steps).

`@slow` — boots a real Isaac server on an ephemeral port (port 0), drives it with the real proxy. Proves the live wiring both isolated suites stub out (cov2 = handler only; 7p1i = stub server).

### Scenarios
1. **batch round-trip**: server started; `remote ws://localhost:${server.port}/cli -- --version` -> stdout contains "isaac", exit 0.
2. **auth enforced** (the 401-once point): server with `server.auth.token`; `remote …` with NO `--token` -> stderr "authentication failed", exit 1 (mirrors acp_websocket reject).
3. **valid token accepted**: same server; `remote … --token secret123 -- --version` -> stdout + exit 0.
4. **MARQUEE / day-1 acceptance (M3)**: `remote …/cli -- acp` drives a full ACP session end-to-end — feed ACP JSON-RPC on stdin, assert the `session/new` response on local stdout, exit 0. Proves acp runs remotely over the generic channel (subsumes the bespoke acp proxy).

### Blocked by isaac-opc4
The harness loads agent+server steps and uses `Given config:` (server host/port/auth) + the manifest index — so it hits the `config:` step collision. Resolve opc4 first.
