---
# isaac-9khs
title: iMessage records :channels on inbound and reports :channel on send (mve9 follow-up)
status: completed
type: feature
priority: normal
created_at: 2026-10-06T20:02:38Z
updated_at: 2026-10-06T21:00:48Z
blocked_by:
    - isaac-mve9
---

Follow-up to isaac-mve9: so deliveries posted into a iMessage channel (cron to, attention, comm__send from another session) land in the owning session's transcript as a marked note, iMessage's inbound must add "imessage:<channel>" to the session's :channels and its send! result must report :channel. Motivating case for iMessage: red-alert's calendar pings to Micah on zanebot never reach the zane iMessage session. Scenarios TBD (mirror the gchat scenario in isaac-gchat outbound.feature).

## Acceptance (Micah 2026-10-06; gated)
Design: isaac/doc/design-conversations-and-channels.md. Supersedes the scrapped isaac-ugvu.
- The @wip scenario in isaac-imessage `features/comm/imessage/channel_continuity.feature` passes with @wip removed: inbound adds `"imessage:<chat-guid>"` to the session's `:channels`; `send!` reports `:channel` as the chat id the addressed handle's messages live in.
- isaac-imessage pins the agent sha carrying isaac-mve9; pins coherent.
- `bb ci` green.


feature-baseline: isaac-imessage 90f9e832066ea59153b7e6dc5f64a57feb502b04
feature-blob: isaac-imessage features/comm/imessage/channel_continuity.feature 67835d0a66b3e6b6488dd94ffdc7a68b3418c0ef

## Landed on main (2026-10-06)

main-sha: isaac-imessage c1af265cdfa08528b4f2091f3bdf91edb8450c49

Inbound records the chat GUID in session channels; send reports imsg's returned chat GUID. Own replies carry the canonical session id to prevent duplicate marked notes. Agent and foundation pins are coherent. `bb ci` and `bb bean-gate verify isaac-9khs` passed.
