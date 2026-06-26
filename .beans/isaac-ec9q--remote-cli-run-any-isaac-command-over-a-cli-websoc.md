---
# isaac-ec9q
title: 'Remote CLI: run any isaac command over a /cli websocket (client + server)'
status: draft
type: epic
created_at: 2026-06-26T20:58:42Z
updated_at: 2026-06-26T20:58:42Z
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
