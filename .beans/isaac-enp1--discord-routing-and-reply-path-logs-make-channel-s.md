---
# isaac-enp1
title: Discord routing and reply-path logs make channel-session resolution diagnosable
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-06-26T16:24:37Z
updated_at: 2026-06-26T17:49:55Z
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

## Approved scenarios

- `isaac-discord/features/comm/discord/routing.feature:69`
  - `an accepted Discord message logs its resolved routing`
- `isaac-discord/features/comm/discord/routing.feature:99`
  - `creating a new Discord session logs the created session name`
- `isaac-discord/features/comm/discord/routing.feature:223`
  - `an invalid Discord config logs a routing config-load failure`
- `isaac-discord/features/comm/discord/reply.feature:47`
  - `a reply for an unmapped session logs a warning instead of silently dropping`

## Decision (2026-06-26, Micah)

Keep this bean in the existing Discord feature files:

- routing breadcrumbs live in `features/comm/discord/routing.feature`
- reply-path warning lives in `features/comm/discord/reply.feature`

This bean adds one new step:

- `When the current session receives a completed turn with text "{text}"`

It is needed only for the negative outbound case, where there is no inbound
Discord message to establish a channel mapping.

## Acceptance commands

- `cd isaac-discord && bb features features/comm/discord/routing.feature features/comm/discord/reply.feature`
