---
# isaac-gxro
title: "Discord: typing indicator during turn generation"
status: completed
type: feature
priority: low
created_at: 2026-04-20T23:07:24Z
updated_at: 2026-04-21T22:48:19Z
---

## Description

Milestone 5 of the Discord channel adapter epic. POST /channels/{id}/typing while the crew is generating, so users see the standard '...' typing dots. Typing is cleared automatically by Discord after 10 seconds, so the channel refreshes periodically while the turn is in flight.

Triggered by on-turn-start, stopped by on-turn-end.

Depends on bead 4.

## Acceptance Criteria

1. Implement typing POST on turn-start for Discord-originated sessions.
2. Add the 'an outbound HTTP request to "<url>" matches:' step-def (shared with isaac-61ob and the reply.feature retrofit).
3. Remove @wip from the scenario in features/comm/discord/typing.feature.
4. Update the matching scenario in features/comm/discord/reply.feature to use the new URL-keyed step (this retrofit already lives in the feature file as @wip equivalent — the step just needs to exist).
5. bb features features/comm/discord/typing.feature passes.
6. bb features features/comm/discord/reply.feature still passes.
7. bb features passes overall.
8. bb spec passes.

## Design

Implementation notes:
- POST /channels/{channel_id}/typing on turn start; no body needed, just auth header.
- Trigger from the Discord Comm impl's on-turn-start hook.
- Long-turn refresh: schedule a repeat POST every ~9 seconds (Discord bubble clears at 10s). Cancel on turn-end. Covered by unit spec, not feature.
- Re-use the existing Discord REST client from milestone 4 (isaac-lkiy).

New step needed (also used by isaac-61ob):
- 'an outbound HTTP request to "<url>" matches:' (k-v table; optional #index meta-row to select among multiple requests to the same URL). Finds the specified recorded request to the URL and asserts table fields. Fails with a clear message if none match.

