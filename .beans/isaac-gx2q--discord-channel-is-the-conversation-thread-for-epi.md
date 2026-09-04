---
# isaac-gx2q
title: Discord channel is the conversation thread for episode crews
status: completed
type: bug
priority: high
tags:
    - discord
    - episodes
created_at: 2026-08-31T03:27:23Z
updated_at: 2026-09-04T20:01:07Z
parent: isaac-51xy
blocked_by:
    - isaac-7dkp
---

Likely repo: **isaac-discord**. Parent: isaac-51xy. Blocked by isaac-mrfu.

## Problem

Discord `process-message!` creates session `discord-<channel-id>` and `charge/build`s that as `:session-key`. Even after the agent router runs, typing/`on-turn-end` map the session name back to a channel via the `discord-` prefix. After routing, the turn session is an episode id, so replies `unmapped-session` and Discord looks dead.

## Decision (2026-08-30, Micah)

The Discord channel is the **conversation thread** (`discord-<channel-id>`), same role as an ACP session id. Do not pre-create that name as the turn session for episode crews. Replies and typing use origin `:channel-id`. Chronicle crews still map channel → session `discord-<channel-id>`. `routing.feature` stays that contract.

## Behavior

- Episodes crew, first MESSAGE_CREATE → `:episodes/opened` with thread `discord-C999`; no session `discord-C999`; REST reply to C999.
- Warm second message in that channel → same episode; both replies still POST to C999.
- Chronicle crew → session `discord-C999`; 0 episodes; reply still works.

Pass thread + `:origin {:kind :discord :channel-id …}` into dispatch. Do not reimplement `resolve-thread!` in isaac-discord.

## Features (`@wip`)

`features/comm/discord/episodes.feature`

- :14 first message on an episodes crew opens an episode and replies to the channel
- :44 a warm second message on the same channel appends and still replies to the channel
- :83 a chronicle crew still maps the channel to session discord-<channel-id>

New steps: none. Episode assertions come from isaac-agent `episode-steps` (`isaac.**-steps`).

## Acceptance

```
cd isaac-discord
bb features features/comm/discord/episodes.feature:14
bb features features/comm/discord/episodes.feature:44
bb features features/comm/discord/episodes.feature:83
```

Remove `@wip` when green. `routing.feature` and `reply.feature` stay green.

Pin isaac-agent to isaac-mrfu's SHA before this can go green in CI.

## Out of scope

- Deleting or compacting zanebot's stuffed `#general` chronicle session.
- Per-channel `:session` override behavior.
- iMessage.



## Summary of Changes (2026-09-04, planner)
Delivered by isaac-7dkp (Discord dispatches `:conversation {:kind :thread :id "discord-<channel>"}` + origin; bridge routes to the episode) and isaac-o0bk / isaac-ay0s (replies and typing route by the cycle's origin). `features/comm/discord/episodes.feature` is active and green on isaac-discord main (0.1.13). Field evidence: marvin's Discord messages open episodes on the channel thread (2026-09-03-1647-xsdf, 2026-09-04-1414-nlve) with replies delivered. The stale block on the completed 7dkp is cleared by closing this bean.
