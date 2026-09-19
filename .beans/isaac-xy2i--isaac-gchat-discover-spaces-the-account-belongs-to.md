---
# isaac-xy2i
title: 'isaac-gchat: discover spaces the account belongs to instead of listing every space in config'
status: todo
type: feature
priority: normal
tags:
    - google
    - comm
created_at: 2026-09-19T21:13:13Z
updated_at: 2026-09-19T21:13:13Z
parent: isaac-bv1l
---

Micah, 2026-09-19 (first yopp rollout): listing every space in comms.gchat.gchat/spaces is the Discord channel-map chore again; the entries exist for two reasons — the gate fails closed on unlisted spaces, and the registration timer subscribes per configured key.

Add `gchat/spaces :all` (or `gchat/discover true`): on each registration tick, `spaces.list` (Chat API, user auth, filter SPACE and DIRECT_MESSAGE as configured) yields the spaces the account is a member of; the timer subscribes to each; the gate routes unlisted-but-discovered spaces with defaults (session gchat-<space>, :respond :mentions in spaces / :all in DMs, crew = gchat/crew default). Explicit entries remain overrides. A space the account leaves is unsubscribed on the next tick (existing delete path). Inviting the account to a space is now granting ingest — document that.

Scenarios (worker writes; registrations.feature + inbound.feature): discovered space subscribed on first tick; a mention in a discovered space starts a turn on the default session; explicit entry overrides crew; leaving a space unsubscribes; discovery off ⇒ unlisted still drops.
