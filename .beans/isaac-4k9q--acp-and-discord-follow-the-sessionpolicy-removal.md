---
# isaac-4k9q
title: ACP and Discord follow the SessionPolicy removal
status: todo
type: bug
priority: high
created_at: 2026-10-05T17:51:02Z
updated_at: 2026-10-05T17:51:02Z
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
