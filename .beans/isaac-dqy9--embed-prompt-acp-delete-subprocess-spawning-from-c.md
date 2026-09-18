---
# isaac-dqy9
title: Embed prompt + acp; delete subprocess spawning from cli-server (end cap)
status: todo
type: feature
priority: normal
tags:
    - cli
created_at: 2026-09-17T15:55:25Z
updated_at: 2026-09-18T01:39:35Z
parent: isaac-eqkb
blocked_by:
    - isaac-qvhy
    - isaac-gar0
    - isaac-kjzq
    - isaac-kk0o
    - isaac-ow5u
    - isaac-x2lp
---

Child 5 of isaac-eqkb (end cap). Blocked by children 2, 3, 4.

## Work

- `prompt` and `acp` run embedded: their sessions are created/written through the server's live store under the JVM persist lock (isaac-4zr3) — the single-writer goal. Embedded `prompt` goes through the same turn gate as every other turn (honors `:max-in-flight`; one turn per session).
- `acp` over the pipe: long-lived duplex on a hosted stream; editor closes stdin → acp returns → exit frame. Server restart drops the stream; proxy reattach gets unknown-stream-id and exits; editor reconnects (accepted, see epic).
- **Delete subprocess spawning from isaac-cli-server**: `babashka.process`, `*spawn-process*`, `*launcher-command*`, `spawn-options`, the `:hosted` transitional marker (child 3), and the spawn-stub feature steps. One-time acceptance criterion (not a permanent scenario): `grep -rn "babashka.process\|spawn-process" isaac-cli-server/src` is empty.
- PROTOCOL.md final wording, both repos.

## Acceptance (draft — scenarios at promotion)

- isaac-cli-proxy `features/integration.feature` remote-ACP e2e (isaac-lcay's scenario) green against embedded acp.
- a `prompt` sent over the pipe while the same session has a turn in flight queues/refuses per the turn gate instead of writing concurrently (spec: two writers, every transcript line parses — jz6h's assertion, now across the CLI boundary).
- zanebot soak: a day of crew traffic with zero `:session/transcript-torn` / `:session/unreadable`.



## Scenarios (committed @wip — isaac-cli-proxy `features/integration.feature` @ 3bc4564, @slow lane)

| line | scenario |
|------|----------|
| :56 | a remote ACP session runs inside the server process |
| :74 | a remote prompt runs inside the server process and its turn is visible to the server |

Both assert `:cli/command-started … :hosted true` in the REAL server's log — cli-server adds `:hosted` to that log entry (true for embedded, false for the transitional subprocess path while it still exists). Note the lcay scenario passes `--root ${fixture.root}`; embedded dispatch refuses a `--root` that is not the server's root, so the fixture server must be started on that root (or the arg dropped, as in :56).

Specs (isaac-cli-server or isaac-agent, worker's call):
1. a second `prompt` over the pipe against a session with a turn in flight is gated by the turn gate (queues/refuses per `:max-in-flight` / one-turn-per-session), and every transcript line parses afterwards (jz6h's assertion across the CLI boundary).
2. embedded `acp` cancellation (grace expiry) closes the session cleanly — no partial record.

## One-time acceptance (not a permanent scenario)
`grep -rn "babashka.process\|spawn-process\|launcher-command\|:hosted" isaac-cli-server/src` is empty after the subprocess path and the transitional marker are deleted; the two isaac-895i subprocess scenarios (`cat`, `sh -c 'exit 3'`) and the `:213` transitional scenario are removed from endpoint.feature, and the recording-spawn-stub steps go with them.

## Step ledger

| step | status |
|------|--------|
| a real cli-server backed by an isaac install with an echo model / isaac remote is run interactively with … / the ACP client steps / the client closes stdin / the exit code is … / stdin is empty / isaac is run with … / the stdout contains … | reuse |
| **the server log has entries matching:** | **NEW — reads the fixture server's log (same table shape as cli-server's `the cli log has entries matching:`)** |

One new step.

## Acceptance
```
cd isaac-cli-proxy && bb features-slow features/integration.feature && bb ci
cd isaac-cli-server && bb features && bb spec && bb ci
```
zanebot soak after the train: a day of crew traffic with zero `:session/transcript-torn` / `:session/unreadable`. PROTOCOL.md final wording, both repos.
