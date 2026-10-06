---
# isaac-rjeg
title: Discord records :channels on inbound and reports :channel on send (mve9 follow-up)
status: completed
type: feature
priority: normal
created_at: 2026-10-06T20:02:38Z
updated_at: 2026-10-06T20:58:34Z
blocked_by:
    - isaac-mve9
---

Follow-up to isaac-mve9: so deliveries posted into a Discord channel (cron to, attention, comm__send from another session) land in the owning session's transcript as a marked note, Discord's inbound must add "discord:<channel>" to the session's :channels and its send! result must report :channel. Motivating case for iMessage: red-alert's calendar pings to Micah on zanebot never reach the zane iMessage session. Scenarios TBD (mirror the gchat scenario in isaac-gchat outbound.feature).

## Acceptance (Micah 2026-10-06; gated)
Design: isaac/doc/design-conversations-and-channels.md.
- The 2 @wip scenarios in isaac-discord `features/comm/discord/channel_continuity.feature` pass with @wip removed: inbound MESSAGE_CREATE adds `"discord:<channel-id>"` to the session's `:channels`; `send!` reports `:channel` (a configured name resolves to its id).
- isaac-discord pins the agent sha carrying isaac-mve9; pins coherent.
- `bb ci` green.


feature-baseline: isaac-discord ef12986ac63ca15dd3a8ce4e79e8f58631a8e1dc
feature-blob: isaac-discord features/comm/discord/channel_continuity.feature 8435cbc9787a32475c7da7501bd57636453e97db

## Work checkpoint (2026-10-06)
Done: Discord inbound records channel on valid sessions, successful send! reports resolved channel, both baselined scenarios pass without @wip; coherent agent/foundation pins. `ISAAC_GIT=1 bb ci` green (56 native specs, 109 JVM specs, 70 features); `bb bean-gate verify isaac-rjeg --dir isaac-discord=../isaac-discord-rjeg` PASS at e39015c. Implementation pushed on bean/isaac-rjeg.
Next: land isaac-discord bean branch via squash on main and re-run gate. Resume at `../isaac-discord-rjeg/src/isaac/comm/discord.clj:513` (inbound channel update); command `git -C ../isaac-discord fetch origin` then squash-merge from the dedicated worktree.

## Landed on main (2026-10-06)
main-sha: isaac-discord 56bf52d31d2281e7fd335b893b5f2f8067c5d245
