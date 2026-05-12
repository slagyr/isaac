---
# isaac-yexn
title: "Discord: receive MESSAGE_CREATE events + allow-from filter"
status: completed
type: feature
priority: normal
created_at: 2026-04-20T23:07:13Z
updated_at: 2026-04-21T04:09:54Z
---

## Description

Milestone 2 of the Discord channel adapter epic. Receive MESSAGE_CREATE Gateway events, filter by the configured allow-from list (user ids + guild ids), emit accepted messages into an Isaac-facing event stream.

Accepts: events where author.id is in allow-from.users AND guild_id is in allow-from.guilds. Anyone not on the list → silently ignored (optionally log at debug).

Bot's own messages are always ignored (don't self-trigger).

Out of scope: session routing (bead 3), reply (bead 4).

Depends on bead 1 for the Gateway event stream.

## Acceptance Criteria

1. Implement the MESSAGE_CREATE handler with the three-part filter (user allow-list, guild allow-list, self-id check).
2. Expose an accepted-message queue observable to tests.
3. Add the 4 step-defs above.
4. Remove @wip from all 4 scenarios in features/channel/discord/intake.feature.
5. bb features features/channel/discord/intake.feature passes (4 examples).
6. bb features passes overall.
7. bb spec passes.

## Design

Implementation notes:
- Lives in src/isaac/channel/discord/intake.clj (or inline in gateway.clj behind a clear seam).
- Receives Gateway op-0 dispatches, routes MESSAGE_CREATE events through the filter, drops everything else for now.
- Filter predicate: author.id in allow-from.users AND guild_id in allow-from.guilds AND author.id != bot-id. Bot id captured from the READY event in the gateway bead (user.id field). All three conditions must hold.
- author.bot is informational but not the self-check. Other bots (with bot: true but different ids) may still be on the allow-from list and would be accepted — operator's responsibility.
- Accepted messages land on an in-memory queue the adapter exposes for downstream consumption (later milestone: session routing in isaac-hfsk).
- Logging: accepted messages → debug log with author+channel+content-preview. Rejected messages → no log (would be too noisy).
- Config expansion: allow-from.users and allow-from.guilds are vectors in real config. Test tables use single values for readability; implementation handles N.

Step-defs to add (under spec/isaac/features/steps/discord.clj):
1. 'the Discord client is ready as bot {id:string}' — runs the handshake and captures {id} as the bot's user id (simulating the READY event's user.id).
2. 'the Gateway sends MESSAGE_CREATE:' — table of payload fields (channel_id, guild_id, author.id, content, ...); dispatched as op-0 MESSAGE_CREATE.
3. 'the Discord client accepted a message with:' — asserts the head of the accepted queue matches the table fields.
4. 'the Discord client accepted no messages' — asserts the accepted queue is empty.

## Notes

Implemented Discord MESSAGE_CREATE intake with allow-from user/guild filtering and self-message suppression in isaac.comm.discord.gateway. Added accepted-message queue assertions plus ready-as-bot/message_create feature steps. Verification: bb features features/comm/discord/intake.feature, bb features, bb spec, bb spec spec/isaac/comm/discord/gateway_spec.clj.

