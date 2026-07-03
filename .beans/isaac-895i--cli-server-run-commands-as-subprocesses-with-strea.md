---
# isaac-895i
title: 'cli-server: run commands as subprocesses with streaming duplex IO'
status: draft
type: feature
priority: high
created_at: 2026-07-03T15:34:23Z
updated_at: 2026-07-03T15:35:22Z
---

## Context

The /cli endpoint currently runs (main/run argv) IN the server JVM: buffered-not-streamed output (sent only after exit), cwd accepted but ignored (JVM cannot chdir), and any System/exit in a CLI path kills the server. Unusable for long-lived interactive commands (ACP), dangerous for everything else.

Decision (2026-07-03, Micah): commands run as SUBPROCESSES. Rationale: streaming duplex falls out of piped stdio (required for ACP); real cwd/env; System-exit/OOM/crash contained; socket-drop cleanup = Process.destroy(); bb launcher startup (~tens of ms) is acceptable, and long-lived commands amortize it.

## Design

- Server spawns the isaac launcher with the handshake argv; cwd from handshake (subject to policy), env inherited.
- stdout/stderr piped and framed AS PRODUCED (streaming, not buffered); stdin frames written to the process; stdin-close closes its stdin.
- Process exit -> {"type":"exit","code":N} -> socket close. Socket drop -> process destroyed (until the resume bean lands).
- **Injectable spawn command** (config, default = the real isaac launcher): the test seam — features can spawn `cat` to prove duplex, `sh -c "exit 3"` to prove containment.
- PROTOCOL.md: no wire change; semantics notes updated (streaming, cwd honored).

## Acceptance (scenarios to be committed after review)

- Duplex: interactive subprocess echoes stdin->stdout BEFORE exit (proves streaming).
- Exit-code propagation; System/exit-style termination contained (server serves a subsequent command).
- cwd honored.
- Socket drop kills the subprocess.

## Likely repo scope

isaac-cli-server (dispatch.clj rewrite, ws.clj wiring, PROTOCOL.md semantics).
