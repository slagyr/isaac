---
# isaac-4vj9
title: A send to a channel no session owns opens a session on the sender's crew
status: draft
type: feature
priority: normal
created_at: 2026-10-06T20:24:06Z
updated_at: 2026-10-06T20:24:06Z
blocked_by:
    - isaac-mve9
---

Design: isaac/doc/design-conversations-and-channels.md (rule 2, unowned channel). After a comm posts and reports :channel, if no session's :channels holds "<comm>:<channel>", open a new session for that channel on the sender's crew with the message as its first (assistant) entry, so a reply lands in a conversation that already holds the opening message. Open detail: the opened session must get the id the comm would give it on inbound (e.g. discord-<channel>, imessage:<chat-guid>, gchat-<tenant>-dm-<name>) so a later inbound finds it instead of creating a twin. Scenarios TBD per comm.
