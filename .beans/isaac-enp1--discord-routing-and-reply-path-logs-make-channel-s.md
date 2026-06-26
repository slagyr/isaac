---
# isaac-enp1
title: Discord routing and reply-path logs make channel-session resolution diagnosable
status: draft
type: feature
created_at: 2026-06-26T16:24:37Z
updated_at: 2026-06-26T16:24:37Z
---

## Context

Even when the Discord gateway is connected and accepting messages, the current
runtime gives very little signal about what happened next:

- which session a message routed to
- whether a per-channel override was found
- which crew/model were chosen
- whether a new session was created
- whether a reply could be mapped back to a channel

In the current code, `process-message!` performs routing silently, and
`on-turn-end` silently skips outbound routing when `session->channel-id`
returns nil.

## Draft acceptance

- When a Discord message is accepted, logs emit a routing breadcrumb with at
  least:
  - channel id
  - guild id or DM surface
  - resolved session
  - resolved crew
  - resolved model when present
  - whether a channel override was found
- When a new session is created from a Discord message, logs emit a session
  creation breadcrumb.
- When a reply exists but the runtime cannot map the session back to a channel,
  logs emit a warning instead of silently doing nothing.
- If per-message config load fails during inbound routing, that failure is
  logged clearly.
- Existing gateway accept/reject logs remain consistent.

## Likely repo scope

- `isaac-discord`

## Notes

This bean is about decision visibility, not changing the routing contract. The
separate behavior bug is `isaac-y1ak`.
