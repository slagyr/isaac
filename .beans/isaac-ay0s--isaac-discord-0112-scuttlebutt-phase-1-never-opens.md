---
# isaac-ay0s
title: isaac-discord 0.1.12 (scuttlebutt phase 1) never opens the gateway and drops episode-crew replies — train rolled back
status: completed
type: bug
priority: high
tags:
    - discord
    - scuttlebutt
created_at: 2026-09-04T04:43:20Z
updated_at: 2026-09-04T14:06:43Z
parent: isaac-tuk1
---

Deployed 2026-09-04 04:02Z with the scuttlebutt train (agent 0.1.43 bf43233, discord 0.1.12 f7cc2de). Discord went dark: `:comm/activated discord`, `:lifecycle/started comms.discord`, `:service/started discord`, but no `:discord.client/started` and no gateway connect for 39 minutes, including after a config hot-reload. Rolled back to the 0.1.41 pins at 04:41Z (registry 51b74b43); gateway ready 1s after restart.

## Defect 1 — Reconfigurable via `extend` is invisible to the loader (gateway never starts)
isaac-o0bk moved the `api/Reconfigurable` methods (on-load / on-config-change! / on-unload) from inline deftype methods into the `extend` map alongside Comm. `isaac.config.berths` (foundation) gates lifecycle with `(satisfies? Reconfigurable node)` where `Reconfigurable` is a **def-aliased snapshot** of the protocol map taken at foundation load (berths.clj:15) — later `extend`s never appear in it. Inline methods still pass because the snapshot recognises the protocol's Java interface. So on-load never ran → register-comm! never ran → no registration → DiscordService.start had nothing to connect. Instrumented locally: `satisfies? Comm` true, `satisfies? Reconfigurable` false, single class, no double load. iMessage kept Reconfigurable inline (yleu) and works; ACP has none.
**Fix (done in a scratch worktree, boot scenarios 6/0):** Reconfigurable back INLINE on the deftype, Comm stays extend+defaults; comment explains why. Rule for every comm until the foundation alias is fixed (see the foundation bean): **Comm via extend, Reconfigurable inline.**

## Defect 2 — on-reply resolves the channel from the session key only (episode replies dropped)
`on-reply* [this session-key text]` uses `(session->channel-id cfg session-key)`. For episodes crews (marvin) the session key is the episode id, not `discord-<channel>`, so the channel is nil and `deliver-content!` drops the reply. isaac-7dkp's origin-aware delivery lived in on-turn-end (result carries :origin); the new on-reply has no result. features/comm/discord/episodes.feature is red on main (2 of 3) — o0bk's verify did not catch it (see gate gap).
**Fix options:** (a) drive puts `:origin` (from the charge) into the cycle map passed to on-cycle-start/on-cycle-end; Discord stashes session-key → origin channel at cycle start and on-reply uses it (principled; agent patch release + discord). (b) Discord-only: resolve the episode's :thread from the episodes store by session key (needs crew from the session entry). Planner recommends (a); decide in review.

## Gate gap
The Discord default `bb features` task ran 61–64 scenarios of 69: `lifecycle.feature` and `service_lifecycle.feature` (server boot → client connects) were not in the run that verified o0bk, and episodes.feature was red on main at verify time. Acceptance for this bean: those files are in the gate, green, AND the module suite is run against the SERVER SHA the train will pin (discord pinned server eb51cc48, 23 commits behind the deployed 1207d45).

## Acceptance
    cd isaac-discord
    bb features features/comm/discord/lifecycle.feature features/comm/discord/service_lifecycle.feature features/comm/discord/episodes.feature features/comm/discord/scuttlebutt.feature
    bb features && bb spec   # with deps/bb pinned to the agent + server SHAs the train pins
Then release 0.1.13 and re-run the scuttlebutt train (all five pins from registry b2a16619, discord replaced).



## Landed on main (2026-09-04) — planner-implemented, Micah: "do the work yourself"
main-sha: isaac-agent e1e10e80ccf1e72a3cb82cf85678af950adb8cbd (Release 0.1.44)
main-sha: isaac-discord 3568cc5626db2c8b89bb6ec5041c07f1a4992e5c (Release 0.1.13)

## Summary of Changes
- Agent 0.1.44: the cycle map handed to on-cycle-start/on-cycle-end carries the charge's `:origin`; memory comm records `origin-kind`; scuttlebutt.feature scenario "cycle events carry the turn's origin"; full gate 763/0 features, 1626/0 spec.
- Discord 0.1.13: Reconfigurable back INLINE on the deftype (with the why-comment); Comm stays extend+defaults; `on-cycle-start` stashes session-key → origin channel and starts the typing heartbeat for episode sessions (first cycle only); `on-reply` and `on-turn-end` resolve the channel via the stash before the session-name map; concurrent worker fix 3abd4e9 (same inline lifecycle + host-state-dir fallback) merged in; qomx's count step ported and its three typing scenarios activated; scuttlebutt scenario 3 rewritten (chatter+tool in one cycle); pins agent 0.1.44 + server 1207d45. Gates (ISAAC_GIT=1, pinned): boot/episodes/scuttlebutt/typing 16/0, default features 67/0, spec 45/0.
- Deployed 14:05Z (registry f66021bf): discord.client/started 14:05:27.08, gateway ready 14:05:28.51, pong OK.

## Gate findings for isaac-jndk
1. The default Discord `bb features` honours `dev-local?` — with a sibling isaac-agent checkout (every worker role home has one) the suite runs against the SIBLING agent/server/foundation sources, not the pinned SHAs. o0bk verified green that way while the pinned run was red. Verify must set `ISAAC_GIT=1`.
2. foundation test-support's `with-timeout!` budget is a fixed 60s (test_timeout.clj:8); the Discord suite finishes in 43s + JVM boot and exits 124 on a green run. Exit codes are not trustworthy under the wrapper until the budget is configurable (`clojure -M:features` unwrapped is).
