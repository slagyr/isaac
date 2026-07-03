---
# isaac-exi2
title: 'isaac-acp: pure stdio module — delete websocket transport and chat command'
status: draft
type: task
priority: normal
created_at: 2026-07-03T15:34:48Z
updated_at: 2026-07-03T15:35:22Z
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

## Acceptance (scenarios after review)

- `isaac acp --remote x` -> unknown option error.
- `isaac chat` -> unknown command.
- /acp route absent from resolved config routes.
- Local stdio scenarios keep passing untouched.
- bb spec / bb features green in isaac-acp.

Blocked by the e2e proof bean.
