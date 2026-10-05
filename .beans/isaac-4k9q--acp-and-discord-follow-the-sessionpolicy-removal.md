---
# isaac-4k9q
title: ACP and Discord follow the SessionPolicy removal
status: todo
type: bug
priority: high
created_at: 2026-10-05T17:51:02Z
updated_at: 2026-10-05T18:56:36Z
blocked_by:
    - isaac-ka10
---

Follow-up to isaac-c52a (SessionPolicy deleted from agent) and isaac-ka10 (episodes as context mode + session observer). Found 2026-10-05 while preparing the zanebot deploy: a scan of every isaac-* repo for `session.policy` / `SessionPolicy` / `session-policy`.

- **isaac-acp (runtime blocker):** `src/isaac/comm/acp/server.clj` requires `isaac.agent.session.policy` and calls `policy/for-crew`, `open-session!`, `get-session`, `active-transcript`. On an agent with c52a the namespace is gone, so ACP fails to load. Port to the session store API (`isaac.agent.session.store`). `spec/.../server_spec.clj` and `acp_steps.clj` (requires `isaac.session.episodes.policy`, deleted by ka10) follow.
- **isaac-discord (tests only):** `spec/isaac/comm/discord_spec.clj:323` sets `[:crew "main" :session-policy] :episodes` (also: no crew named "main" — use the scenario's crew).
- Both repos pin agent at the c52a+ sha and episodes at ka10's landing sha (pins coherent with agent's foundation pin).
- isaac-handbook `features/reference.feature` was updated in place (ceb7879) under its own draft bean isaac-niqx; not this bean.

## Acceptance (gated)
- The @wip scenarios in isaac-acp `features/comm/acp/episodes.feature` (5) and isaac-discord `features/comm/discord/episodes.feature` (2) pass with @wip removed (crew rows moved to `:context-mode :episodes` + `:observers [:episodes]`).
- One-time: no `session.policy` / `SessionPolicy` / `session-policy` / `episodes.policy` reference remains in isaac-acp or isaac-discord src/spec/features.
- `bb ci` green in isaac-acp and isaac-discord against the pinned shas (`ISAAC_GIT=1`, not dev-local siblings).
- Ships in the same zanebot deploy as c52a + ka10.

Likely repo scope: isaac-acp, isaac-discord.

feature-baseline: isaac-acp 2bedd796e346233c008ef4d7235f359c669d7551
feature-baseline: isaac-discord d552bf07f3f6795e9f13039aabb4a6cc2d6cac60
feature-blob: isaac-acp features/comm/acp/episodes.feature 602dfe1902e63407726ad132896f3ebba2650b77
feature-blob: isaac-discord features/comm/discord/episodes.feature e96df87a95e59954da0042c7688deda3f87517c1

## Conflict (2026-10-05, scrapper@isaac-work-2)

Implementation ported on `bean/isaac-4k9q`. Specs green (`ISAAC_GIT=1`).
Features blocked by baselined scenario text that contradicts ka10's
landed episodes module. Worker cannot edit `.feature` files beyond `@wip`
removal and cannot change isaac-episodes.

Pins: agent `f05a1fe` (c52a+ / ka10 origin/main), episodes `541f9f9`
(ka10 landing). ACP server uses `store/open-session!`, `get-session`,
`active-transcript`. Discord spec crew is cordelia with
`:context-mode :episodes` + `:observers [:episodes]`.

### isaac-acp features/comm/acp/episodes.feature — 4/5 pass

Fail:

```
session/prompt on an episodes crew opens an episode with the ACP session as session id
Expected: []
got: ["Row 0: Row 0: session-id: Expected \"reef-chat\", got: nil"]
```

`:episodes/opened` logs `:thread` (isaac-episodes `lifecycle.clj:181`),
not `:session-id`. The table column cannot be satisfied without editing
the feature or isaac-episodes.

### isaac-discord features/comm/discord/episodes.feature — 2/3 pass

Fail:

```
first message on an episodes crew opens an episode and replies to the channel
id: Expected match for (?s)\d{4}-\d{2}-\d{2}-\d{4}-\w+, got: "20261005185354872"
```

ka10 episode ids are 17-digit timestamps (`#"\d{17}"` — already used in
the ACP feature). Discord still asserts the old session-id slug pattern.

Branches pushed: isaac-acp `38e6cb7`, isaac-discord `e91496e`.

feature-baseline: isaac-acp 19620e7bb13fbe42bafa506a9b2bbea7bee5a9e1
feature-baseline: isaac-discord b1b0820e5911433de35c45d8dfdfe041229fb5a8
feature-blob: isaac-acp features/comm/acp/episodes.feature 83c80a4b30e21a90d3dd670fbdcedbd2e4664bf8
feature-blob: isaac-discord features/comm/discord/episodes.feature c5fa79e6ea41174c9abf6f1d7f6c595d63f8d11b

## Planner adjustment (2026-10-05, prowl@isaac-plan)

ka10's shape stands.

- isaac-acp `episodes.feature`: the opened-log column is `thread`, not `session-id`. Value stays `reef-chat`. Episode regex stays `\d{17}`.
- isaac-discord `episodes.feature`: the episode id regex is `\d{17}`, not the old slug.

Both stay `@wip`. On acp main `19620e7`, blob `83c80a4b`. On discord main `b1b0820`, blob `c5fa79e6`. Both files are line-less.

Rebase onto those shas. Drop `@wip` only. Do not edit frozen scenario text.
