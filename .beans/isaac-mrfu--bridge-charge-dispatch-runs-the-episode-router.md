---
# isaac-mrfu
title: Bridge charge dispatch runs the episode router
status: in-progress
type: bug
priority: high
tags:
    - episodes
created_at: 2026-08-31T03:25:29Z
updated_at: 2026-08-31T14:47:52Z
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


## Verify fail (attempt 1, 2026-08-31): required episodes live regression is red

Evidence:
- Verified implementation exists on `isaac-agent` `origin/bean/isaac-mrfu` at `2f9b889` (`isaac-mrfu: dispatch! of a pre-built charge runs the episode router`).
- The bean's accepted charge-dispatch scenarios are green on the bean branch:
  - `bb features features/bridge/episode_dispatch.feature:12` → `1 examples, 0 failures, 4 assertions`
  - `bb features features/bridge/episode_dispatch.feature:36` → `1 examples, 0 failures, 5 assertions`
  - `bb features features/bridge/episode_dispatch.feature:70` → `1 examples, 0 failures, 2 assertions`
  - `bb features features/bridge/episode_dispatch.feature` → `3 examples, 0 failures, 11 assertions`
- `features/bridge/episode_dispatch.feature` is de-`@wip` on the bean branch.
- However, the bean also explicitly requires `features/episodes/live.feature` to stay green, and that regression is red on the bean branch:
  - `bb features features/episodes/live.feature:160`
  - `1 examples, 1 failures, 2 assertions`
  - failing step: `Then the stdout contains "here is the answer"` at `episodes/live.feature:181`
- I also reproduced the same red scenario on current `origin/main` (`506fea4`), so this may be an ambient/base regression rather than new damage from `isaac-mrfu`; but under the bean's current text, the required live regression net is still not green.
- Focused spec coverage for the implementation seam is green:
  - `bb spec spec/isaac/bridge_spec.clj` → `60 examples, 0 failures, 129 assertions`

Conclusion: the new charge-dispatch behavior is implemented and the bean-specific feature is green, but DoD is still unmet because the required `features/episodes/live.feature` regression is red. Either make that regression green on the authoritative branch or get planner approval to narrow/split the ambient live regression requirement.

## Worker return (2026-08-31, scrapper@isaac-work-2)

Reproduced `bb features features/episodes/live.feature:160` on `origin/bean/isaac-mrfu` @ `2f9b889` — same 1/1 at :181, stdout missing `"here is the answer"`.

This is **not** mrfu damage. Same scenario is red on `origin/main` (`506fea4`). Root cause is the isaac-p9zy content-only prompt estimate: the live compaction-close fixture is the same short-transcript / 200-window shape as `context_management` / `compaction_logging`. Content chars/4 never reaches `0.8 * 200`, compaction never fires, the successor episode is never seeded, and the follow-up echo (`here is the answer`) is never consumed.

Sibling conflict already filed on **isaac-x2up** (plan hail `2dc69b65`): p9zy's content-only estimator cannot coexist with these short-transcript compaction fixtures without weakening either p9zy or the fixtures. live.feature:160 is one of those fixtures.

mrfu product (`dispatch!` of a pre-built charge through `ensure-session!`) and `episode_dispatch.feature` stay green and should not be rewritten to paper over this.

Returning for planner: drop / split the `features/episodes/live.feature stays green` gate from mrfu (own it on x2up or a sibling), or wait for x2up's adjustment. Do not weaken live.feature to fit mrfu.
