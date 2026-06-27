---
# isaac-ebl1
title: 'Discord: per-channel routing via the frequencies map'
status: draft
type: feature
priority: normal
created_at: 2026-06-27T16:01:15Z
updated_at: 2026-06-27T16:01:47Z
parent: isaac-4e4b
blocked_by:
    - isaac-rqlc
---

CONFIRMED (2026-06-27): discord inbound routing IS frequencies-shaped. The :discord/channels config maps each channel id to {:crew :model :session :name} — selection (:session default 'discord-<channel-id>', :crew) + override (:model). It builds a charge from that, like cron/hooks.

Adopt frequencies: a channel's routing config becomes a frequencies map (validated by the shared schema), unifying discord's bespoke per-channel schema with the rest. Inbound message -> channel frequencies -> resolved session + override -> turn. Blocked-by the frequencies rename.
