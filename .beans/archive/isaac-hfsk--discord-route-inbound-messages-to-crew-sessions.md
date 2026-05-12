---
# isaac-hfsk
title: "Discord: route inbound messages to crew sessions"
status: completed
type: feature
priority: normal
created_at: 2026-04-20T23:07:17Z
updated_at: 2026-04-21T15:58:38Z
---

## Description

Milestone 3 of the Discord channel adapter epic. Given an accepted Discord MESSAGE_CREATE, resolve the session key (agent:<crew>:discord:<channel-id>:<user-id>) and invoke the drive turn just like the CLI and ACP channels do.

Behavior:
- First message from a (user, channel) pair creates a new session
- Subsequent messages append to the same session
- Crew id comes from :channels :discord :crew in config (default :main)
- Implements the isaac.channel protocol so on-turn-start, on-tool-call, etc. hooks work for Discord the same way they do for CLI

Out of scope: actually sending the reply back to Discord (bead 4).

Depends on bead 2.

## Acceptance Criteria

1. Implement routing-table persistence at <state-dir>/comm/discord/routing.edn with nested shape.
2. Wire accepted MESSAGE_CREATE → routing lookup → session create-or-find → process-user-input!. Respect :sessions :naming-strategy via isaac-atpy.
3. Add the 2 EDN-file step-defs. Rename 'the Gateway sends' → 'Discord sends' to match gateway.feature / intake.feature / routing.feature.
4. Remove @wip from both scenarios in features/comm/discord/routing.feature.
5. bb features features/comm/discord/routing.feature passes (2 examples).
6. bb features passes overall.
7. bb spec passes.

## Design

Implementation notes:
- Routing table persisted at <state-dir>/comm/discord/routing.edn as nested map: {"<channel-id>" {"<user-id>" "<session-name>"}}. Nested shape lets the generic 'EDN file contains:' step assert via dot-paths.
- On each accepted MESSAGE_CREATE (after allow-from + self-filter from milestone 2):
  1. Look up (channel_id, user_id) in the routing table.
  2. If present: route to that session.
  3. If absent: create a new session via storage/create-session! (respecting :sessions :naming-strategy), record the (channel_id, user_id) → session-name mapping, persist the table, then route.
- Routing dispatches through drive/turn/process-user-input! just like CLI and ACP channels. Same Comm protocol contract.
- Crew id comes from :comms :discord :crew config (default :main).

Depends on isaac-atpy for deterministic session naming in tests. Without it, the second scenario can't assert 'session-1' reliably.

New step-defs to add:
- 'the EDN file "<path>" contains:' (table with path|value rows) — dual Given/Then. Given: writes a nested map built from the paths. Then: asserts each path resolves to the expected value; extra keys OK.
- 'the EDN file "<path>" does not exist' — Given, removes or never-creates the file.
- 'Discord sends MESSAGE_CREATE:' — table of payload fields; the fake Gateway dispatches to the client. (May already exist from milestone 2 under the old 'the Gateway sends' name — just update.)

