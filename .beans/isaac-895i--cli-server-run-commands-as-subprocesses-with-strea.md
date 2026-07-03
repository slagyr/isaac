---
# isaac-895i
title: 'cli-server: run commands as subprocesses with streaming duplex IO'
status: in-progress
type: feature
priority: high
tags:
    - unverified
created_at: 2026-07-03T15:34:23Z
updated_at: 2026-07-03T21:04:35Z
---

## Context

The /cli endpoint currently runs (main/run argv) IN the server JVM: buffered-not-streamed output (sent only after exit), cwd accepted but ignored (JVM cannot chdir), and any System/exit in a CLI path kills the server. Unusable for long-lived interactive commands (ACP), dangerous for everything else.

Decision (2026-07-03, Micah): commands run as SUBPROCESSES. Rationale: streaming duplex falls out of piped stdio (required for ACP); real cwd/env; System-exit/OOM/crash contained; socket-drop cleanup = Process.destroy(); bb launcher startup (~tens of ms) is acceptable, and long-lived commands amortize it.

## Design

- Server spawns the isaac launcher with the handshake argv; cwd from handshake (subject to policy), env inherited.
- stdout/stderr piped and framed AS PRODUCED (streaming, not buffered); stdin frames written to the process; stdin-close closes its stdin.
- Process exit -> {"type":"exit","code":N} -> socket close. Socket drop -> process destroyed (until the resume bean lands).
- **Contract: argv never selects the binary.** The server always spawns the configured isaac launcher; `isaac` is implied and the client argv is applied to isaac main verbatim. There is no way to run an arbitrary program.
- **Injectable spawn stub is TEST SCAFFOLDING ONLY** (proving transport properties — duplex via `cat`, containment via `sh` — without a full isaac install in the test sandbox). Production configuration has no binary override.
- **cwd REMOVED from the wire protocol** (decision 2026-07-03, Micah): the proxy's cwd is a client-machine path, meaningless on the server filesystem; isaac commands use the server's root. Delete the `cwd` field from the start frame in PROTOCOL.md (both repos, lockstep); server ignores/rejects it.
- PROTOCOL.md: semantics notes updated (streaming; cwd field removed).

## Acceptance (scenarios to be committed after review)

- Duplex: interactive subprocess echoes stdin->stdout BEFORE exit (proves streaming).
- Exit-code propagation; System/exit-style termination contained (server serves a subsequent command).
- cwd honored.
- Socket drop kills the subprocess.

## Likely repo scope

isaac-cli-server (dispatch.clj rewrite, ws.clj wiring, PROTOCOL.md semantics).

## Acceptance scenarios (committed @wip, 2026-07-03)

isaac-cli-server `features/cli/endpoint.feature` — 2 scenarios (streaming duplex via `cat`; exit containment via `sh -c "exit 3"` + follow-up command). All 4 scenarios committed @wip: streaming duplex (`cat`), exit containment (`sh -c "exit 3"`), isaac-implied argv contract (recording spawn stub), kill-on-disconnect. New steps approved: spawn-command stub Given, recording-stub Given + spawn assertion, client-disconnects When, subprocess-not-running Then.

Acceptance: un-@wip; `bb spec` / `bb features` green in isaac-cli-server.

Note: the kill-on-disconnect scenario is intentionally unconditional in THIS bean and is superseded by isaac-4tn1's grace-window semantics when that lands.
