---
# isaac-vikl
title: "Discord gateway logs polling transport error maps as gateway errors"
status: completed
type: bug
priority: normal
created_at: 2026-05-09T19:39:56Z
updated_at: 2026-05-11T18:11:06Z
---

## Description

After pulling latest main, bb verify is red in modules/isaac.comm.discord/spec/isaac/comm/discord/gateway_spec.clj:217. The spec 'treats polling transport error maps as structured gateway errors' expected both :discord.gateway/disconnected and :discord.gateway/error events but only saw :discord.gateway/disconnected. This is outside isaac-3usy / isaac-vqx3 scope and should be tracked separately.

## Acceptance Criteria

bb verify passes again; modules/isaac.comm.discord/spec/isaac/comm/discord/gateway_spec.clj 'treats polling transport error maps as structured gateway errors' passes with the expected error + disconnected events

