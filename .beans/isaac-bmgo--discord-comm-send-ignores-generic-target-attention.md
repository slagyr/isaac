---
# isaac-bmgo
title: Discord comm send! ignores generic :target — attention posts 405 with nil channel
status: in-progress
type: bug
priority: high
tags:
    - unverified
created_at: 2026-07-10T12:32:20Z
updated_at: 2026-07-10T12:38:30Z
---

## Bug

Comm delivery records (comm/delivery/pending) carry the generic :target key, but the discord Comm's send! resolves the channel from :discord/target only (isaac-discord src/isaac/comm/discord.clj ~line 301: (resolve-target-channel dcfg (:discord/target record))). Result: channel-id nil -> POST to a malformed URL -> Discord 405 Method Not Allowed -> non-transient -> comm delivery dead-letters as :permanent.

## Observed (2026-07-10, zanebot — isaac-o9me replay)

Every isaac-5a4n/isaac-dark attention post dead-lettered: :discord.reply/http-error :channelId nil :status 405, then :comm.delivery/dead-lettered :reason :permanent. Reproduced with target as channel name, numeric id, and string id — the target value never mattered because it was never read.

## Fix

- send! resolves the channel from (or (:discord/target record) (:target record)).
- resolve-target-channel accepts both a raw channel id and a configured channel NAME (the :discord/channels map values carry :name) — attention config should be writable as {:target "isaac"}.
- Scenario coverage: send! with generic :target (id) posts to that channel; with a configured channel name resolves the id; nil/unknown target -> {:ok false :transient? false} WITH a log naming the bad target (the 405 path today is silent about why).

## Context

Found by the isaac-o9me post-deploy replay of the context-window guard. The guard works (hail deferred, zero attempts, comm queued); this bug kills the attention delivery at the last hop.

## Worker note (2026-07-10, scrapper@isaac-work-1)

- `isaac-discord` branch `bean/isaac-bmgo` @ `71f305e`: `send!` uses `(or :discord/target :target)`; blank resolved channel → `{:ok false :transient? false}` + `:discord.send/missing-target` warn (no HTTP).
- Specs + `features/comm/discord/comm_send_target.feature` extended for generic `:target` id and channel name.
- `bb ci` green on branch.
