---
# isaac-ros0
title: 'Address a delivery to a session: send on its most recent channel, record there'
status: draft
type: feature
priority: normal
created_at: 2026-10-06T20:24:06Z
updated_at: 2026-10-06T20:24:06Z
blocked_by:
    - isaac-mve9
---

Design: isaac/doc/design-conversations-and-channels.md (rule 1). A send (comm__send, cron to, attention) may target a session instead of a comm target; it goes out on the channel the session was most recently reached through (override by naming one of its :channels) and is recorded in that session. Open details: whether 'most recent' reads :last-channel/:last-to or the newest :channels entry; a session with no channel (hail/CLI-only) addressed without a channel is an error, not a silent drop. Scenarios TBD.
