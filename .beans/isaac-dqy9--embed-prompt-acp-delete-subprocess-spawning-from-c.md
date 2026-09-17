---
# isaac-dqy9
title: Embed prompt + acp; delete subprocess spawning from cli-server (end cap)
status: draft
type: feature
priority: normal
tags:
    - cli
created_at: 2026-09-17T15:55:25Z
updated_at: 2026-09-17T15:55:25Z
parent: isaac-eqkb
blocked_by:
    - isaac-1fwl
    - isaac-qvhy
    - isaac-gar0
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
