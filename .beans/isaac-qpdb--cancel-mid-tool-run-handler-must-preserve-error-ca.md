---
# isaac-qpdb
title: 'Cancel mid-tool: run-handler must preserve {:error :cancelled} (ACP tool_call_update pending after 0.1.58)'
status: in-progress
type: bug
priority: high
tags:
    - cancel
created_at: 2026-09-10T13:07:38Z
updated_at: 2026-09-10T13:57:43Z
---

Repo: **isaac-agent** (`src/isaac/tool/registry.clj` `run-handler`) + **isaac-acp** pin. Do **not** reopen **isaac-0yoc**.

CI FAIL hail `da779f10` / run 34475605641: isaac-acp `verify: Run features` 64/1 on land SHA `a129f43` (0yoc / release 0.1.12 `ae798ec`). Same fail locally:

    ISAAC_GIT=1 clojure -M:features features/comm/acp/cancel_tool_status.feature
    # 1/1, 3.74s — after session/cancel expected tool_call_update/cancelled, got tool_call/pending

0yoc acceptance still holds (`session.feature` + recut `episodes.feature` + `grep isaac.episodes` empty). 0yoc diff vs `79cc310` does **not** change `acp.clj` cancel notifications or `session-cancel-handler`. It pins agent `bf43233` (0.1.43) → `837b6d4` (0.1.58 mmod). `837b6d4` vs later agent main `5104ad4` is changelog+manifest only.

## Root cause (isaac-agent, not ACP session/new|load)

- 0.1.43 `record-tool-call!` keeps tool-state `:pending` during exec; `bridge on-cancel!` CAS `pending→cancelled` fires `comm/on-tool-cancel` mid `sleep 30`. Feature green even if execute result is swallowed.
- 0.1.58 `announce-tool-call!` CAS `:announced→:running` **before** `tool-registry/execute`. In-flight `cancel-queued!` CAS `announced→cancelled` **fails**. Cancelled update now requires execute to return `{:error :cancelled}`.
- `exec/exec-tool` still returns `{:error :cancelled}` after destroy. `isaac.tool.registry/run-handler` `:else` does `(cap-output caps result)` on that map → `{:result "{:error :cancelled}"}`. `turn.clj` never sees `:error :cancelled`, so no `on-tool-cancel`. Feature 3s poll then matches leftover pending.

Live: cancel still aborts; editor pending indicator uncleared until this lands and ACP pins it.

## Required

1. **isaac-agent:** `run-handler` must preserve `{:error :cancelled}` (do not stringify/cap). Spec: execute of a handler returning `{:error :cancelled}` keeps that map.
2. **Optional belt:** `on-tool-cancel` while tool-state is `:running` (hook currently only wins at `:announced`).
3. **isaac-acp 0.1.13** pin the agent SHA; gate `features/comm/acp/cancel_tool_status.feature` + `server_spec` session/cancel (**real exec**, not only `redef execute`).
4. Planner releases ACP 0.1.13 when that lands. Do not mutate live config in this bean.

## Acceptance

    cd isaac-agent
    bb spec spec/isaac/tool/registry_spec.clj
    # plus a focused spec that execute of {:error :cancelled} keeps that map

    cd isaac-acp
    # agent pin = the SHA that preserves {:error :cancelled}
    ISAAC_GIT=1 clojure -M:features features/comm/acp/cancel_tool_status.feature
    ISAAC_GIT=1 clojure -M:spec spec/isaac/comm/acp/server_spec.clj

0 failures. Do **not** require full `bb features` wrapper exit 0. Do **not** recut 0yoc product (session/new, session/load, episodes.feature).

## Notes

Related: **isaac-2va** (completed — original `tool_call_update cancelled` contract); **isaac-2bni** (draft flake on `cancellation.feature` timing, different scenario); **isaac-x27m** (completed cancel_aborts_work flake). This bean is the 0.1.58 announce/running + cap-output stringify, not those.

## Planner confirmation (2026-09-10)

Bisect confirms the agent-side root cause: at isaac-acp `79cc310` (pre-0yoc) `clojure -M:dev-local:features features/comm/acp/cancel_tool_status.feature` against local agent 837b6d4 → 1/1 failure; `clojure -M:features` (pinned agent 0.1.43 bf43233) → 1/0. On isaac-acp main both aliases fail. Reverting 0yoc's server.clj or cli.clj changes one at a time does not help. The regression rides whichever agent release introduced the announce→running CAS; 0yoc only moved the pin so CI could see it.

Promoted from draft; dispatching now. Worker: land the agent fix on a bean branch of isaac-agent first (release is the planner's), then in isaac-acp pin that agent SHA and gate the feature with real exec as written above. Note the ACP prompt path is unchanged by 0yoc; do not touch it.
