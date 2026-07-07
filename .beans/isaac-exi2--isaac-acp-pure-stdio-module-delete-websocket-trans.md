---
# isaac-exi2
title: 'isaac-acp: pure stdio module — delete websocket transport and chat command'
status: completed
type: task
priority: normal
created_at: 2026-07-03T15:34:48Z
updated_at: 2026-07-07T15:46:51Z
blocked_by:
    - isaac-lcay
---

## Context

`isaac acp` local is already pure stdio (in-process handlers, stdin/stdout JSON-RPC). The websocket machinery exists only for --remote, now superseded by the generic cli pipe (whose PROTOCOL.md states it generalizes the /acp route + acp --remote proxy).

Decisions (2026-07-03, Micah):
- Clean cutover: no --remote/--token deprecation aliases; flags removed, unknown thereafter.
- Chat (Toad launcher) DROPPED for now — replaced by a doc note/alias (`isaac remote acp ...` in the editor/Toad agent config).
- No dedicated acp proxy; remote UX (reconnect, status) is the generic pipe's job.

## Scope (delete)

- src/isaac/comm/acp/websocket.clj + websocket/heartbeat.clj
- src/isaac/comm/acp/cli/queue.clj (reconnect queue) + connect-remote!/run-remote/reconnect machinery in cli.clj
- --remote/--token option flags (acp + chat)
- manifest /acp route entry (`:isaac.server/route`)
- chat command + chat_cli.clj (add README/doc note for the alias replacement)
- All specs/features covering the above (removed, not retained)

## Keep

- run-local stdio path, session-selection flags, verbose mode, the module berths otherwise.

## Acceptance criteria (one-time checks — deliberately NO scenarios)

Decision (2026-07-03, Micah): absence-of-behavior is never a committed scenario (tombstone tests accrete forever). The verifier runs these ONCE at verification:

- [ ] `isaac acp --remote wss://example` -> unknown option error, exit 1 (must NOT be silently ignored — stale editor configs would quietly run a local agent).
- [ ] `isaac chat` -> unknown command, exit 1.
- [ ] Resolved config route listing contains no `/acp`.
- [ ] `features/cli/chat` feature file and all --remote/websocket specs/features are DELETED (not retained, not @wip).
- [ ] Existing local-stdio scenarios pass untouched; `bb spec` / `bb features` green in isaac-acp.

## Implementation Notes

- Implemented in `isaac-acp` on branch `isaac-exi2-pure-stdio`.
- Commit: `a8b26e6f90679583d590fac85a4ac47b563ee095` (`Remove ACP remote/chat surfaces for pure stdio`).
- Reworked `spec/isaac/comm/acp/acp_steps.clj` into a local-only direct-dispatch harness so kept ACP features no longer depend on websocket/chat/loopback proxy machinery.
- Deleted remote/chat/websocket/proxy/reconnect/queue sources, specs, and features.
- Rewrote `src/isaac/comm/acp/cli.clj` to stdio-only behavior and removed `--remote` / `--token` support.
- Rewrote manifest/README/schema expectations to ACP stdio only; manifest no longer contributes `/acp` or chat.
- Verification run in `isaac-acp`:
  - `bb spec` → 0 failures, 1 pending
  - `bb features` → 0 failures, 5 pending
  - `isaac acp --remote wss://example` → `Unknown option: "--remote"`, exit 1
  - `isaac chat` → `Unknown command: chat`, exit 1

Blocked by the e2e proof bean (isaac-lcay).
