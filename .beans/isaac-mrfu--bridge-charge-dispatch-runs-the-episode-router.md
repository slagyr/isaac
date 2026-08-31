---
# isaac-mrfu
title: Bridge charge dispatch runs the episode router
status: in-progress
type: bug
priority: high
tags:
    - unverified
    - episodes
created_at: 2026-08-31T03:25:29Z
updated_at: 2026-08-31T14:43:17Z
parent: isaac-51xy
---

Likely repo: **isaac-agent**. Parent: isaac-51xy. Blocks the Discord thread-mapping bean.

## Problem

`bridge/dispatch!` runs `resolve-thread!` only for a request map. A pre-built charge (`charge?`) goes to `dispatch-charge!` and skips the episode router. Discord and ACP both `charge/build` then dispatch, so an episodes crew never opens an episode. The inbound session-key is used as a chronicle session.

CLI `prompt --session` already routes (its own `ensure-session!`). This bean is the charge skip.

## Decision (2026-08-30, Micah)

Channel / ACP session id / `--session` is the **conversation thread** (not a process thread). `dispatch!` of a pre-built charge must run the same episode router as a request map. One seam. Chronicle crews unchanged.

## Behavior

- Episodes crew + charge `:session-key` → `resolve-thread!` with `:thread` = that key. Turn runs on the episode id. No chronicle session named after the thread.
- Warm second charge on the same thread → same open episode.
- Chronicle crew → session-key is the session; zero episodes.

Do not reimplement the router in Discord. Origin on `:episodes/opened` is out of scope unless already there.

## Features (`@wip`)

`features/bridge/episode_dispatch.feature`

- :12 a pre-built charge on an episodes crew opens an episode on that thread
- :36 a warm second charge on the same thread appends to the open episode
- :70 a pre-built charge on a chronicle crew uses the session-key as the session

New step: `When a charge is dispatched with:` — must `charge/build` then `dispatch!` (the skip path). CLI prompt would pass today.

Reuse: crew edn, queued echo, episode exists, sessions match, session does not exist, crew has N episodes, current time.

## Acceptance

```
cd isaac-agent
bb features features/bridge/episode_dispatch.feature:12
bb features features/bridge/episode_dispatch.feature:36
bb features features/bridge/episode_dispatch.feature:70
```

Remove `@wip` when green. `features/episodes/live.feature` stays green.

## Out of scope

- Discord channel mapping / replies (follow-on bean).
- `#general` leftover chronicle session (operational).
- Hail `run-turn!` bypass (6yg0 note; sibling later).
