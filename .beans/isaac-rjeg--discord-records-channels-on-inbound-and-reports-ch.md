---
# isaac-rjeg
title: Discord records :channels on inbound and reports :channel on send (mve9 follow-up)
status: draft
type: feature
priority: normal
created_at: 2026-10-06T20:02:38Z
updated_at: 2026-10-06T20:02:38Z
blocked_by:
    - isaac-mve9
---

Follow-up to isaac-mve9: so deliveries posted into a Discord channel (cron to, attention, comm__send from another session) land in the owning session's transcript as a marked note, Discord's inbound must add "discord:<channel>" to the session's :channels and its send! result must report :channel. Motivating case for iMessage: red-alert's calendar pings to Micah on zanebot never reach the zane iMessage session. Scenarios TBD (mirror the gchat scenario in isaac-gchat outbound.feature).
