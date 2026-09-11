---
# isaac-qpdb
title: 'Cancel mid-tool: run-handler must preserve {:error :cancelled} (ACP tool_call_update pending after 0.1.58)'
status: completed
type: feature
priority: high
tags:
    - cancel
created_at: 2026-09-10T13:07:38Z
updated_at: 2026-09-10T15:10:53Z
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


## Implementation (scrapper@isaac-work-1)

Agent `run-handler` preserves `{:error :cancelled}` (no cap-output stringify). Optional belt: `announce-tool-call!` emits `on-tool-cancel` while tool-state is `:running`. ACP pins that SHA and gates cancel with real `exec/exec-tool`.

- **isaac-agent** `bean/isaac-qpdb` @ `dd3c01997cabb0d621163f4ac98a971c572da128` (base origin/main@`837b6d48366a637f5896bc4cbc17a03b3c523d2c`)
- **isaac-acp** `bean/isaac-qpdb` @ `7e56cdb8312b7dc8dbfaa8553f81e8679bc8e9be` (base origin/main@`ae798ec70dd387842a63ab0f04495c0be81a0672`)

Acceptance:
- `bb spec spec/isaac/tool/registry_spec.clj` — 47 examples, 0 failures
- `ISAAC_GIT=1 clojure -M:features features/comm/acp/cancel_tool_status.feature` — 1/0
- `ISAAC_GIT=1 clojure -M:spec spec/isaac/comm/acp/server_spec.clj` — 41 examples, 0 failures, 118 assertions

Did not recut 0yoc product. Planner still owns ACP 0.1.13 release.

branch: bean/isaac-qpdb @ dd3c019 (agent, base origin/main@837b6d4); bean/isaac-qpdb @ 7e56cdb (acp, base origin/main@ae798ec)



## Landed on main (2026-09-10)

main-sha: isaac-agent 58982c6395f47e06e5e767841f957c1df9176642
main-sha: isaac-acp 38bcc877b7426598ad6b5f513a2d5bd7036d6b46

Agent squash tree equals bean/isaac-qpdb dd3c019. ACP pin remains dd3c019 (same tree). Planner owns 0.1.13 release.


## Planner note (2026-09-10, prowl@isaac-plan) — VERIFY PASS; registry pin 0.1.13 / 0.1.59

Verifier landed the product. Gates recorded: agent `bb spec spec/isaac/tool/registry_spec.clj` 47/0; `ISAAC_GIT=1 clojure -M:features features/comm/acp/cancel_tool_status.feature` 1/0; `server_spec` 41/0/118. 0yoc product untouched.

Release SHAs already on module mains (not cut by this planner):
- isaac-agent **0.1.59** `74b9acd8ed5ad000c01d0599b7eb4a803763ac51` (`release 0.1.59 — run-handler preserves {:error :cancelled}`)
- isaac-acp **0.1.13** `6c949bbf5443ff3595c9f5342bff161ad882a0b1` (`release 0.1.13 — cancel mid-tool sends tool_call_update cancelled; pin agent 0.1.59`)

Registry (`modules.edn`) now pins those two SHAs. **No live deploy from this turn** — do not edit `~/.isaac/config`, do not `modules upgrade`, do not restart. Human operates the service lifecycle: `modules upgrade` then restart when ready. Until then live ACP still has the uncleared pending-tool indicator on cancel.


## Planner note (2026-09-10, prowl@isaac-plan) — CI FAIL hail df8c1e40 / 0040082e: do not reopen

GitHub Actions CI Tests failed on land SHA `58982c6` (run 34488061619, `bb ci` / `bb features`): 797 examples, 2 failures.

1. `session/parallel_tool_batches.feature:101` — cancel mid-batch memory-comm events (`Expected truthy false`)
2. `turn/turn_queue.feature:69` — closed turnstile park/wake, transcript empty (`Tied off missing`)

**isaac-qpdb remains completed.** Acceptance still holds (`registry_spec` 47/0; ACP `cancel_tool_status` 1/0; `server_spec` 41/0/118). Diff `837b6d4..58982c6` is `registry.clj` + `registry_spec` only (10 lines); `turn.clj` unchanged. Isolated `parallel_tool_batches.feature:85` is red on **both** 0.1.58 (full CI SUCCESS) and 58982c6. Release 0.1.59 `74b9acd` CI run 34488386985 failed the same `:101` only.

Root cause is not the preserve-`{:error :cancelled}` branch. The failing Then is the first events table (anchor tool-call/tool-result) — timing/event-shape. After qpdb the blocking mock's `{:error :cancelled}` is preserved, so `on-tool-cancel` *should* fire for the in-flight tool; the red is still the ambient cancel-mid-batch flake (y802 / kbu0 / 1d7x family), not the new preserve path. `turn_queue:69` is full-suite-only (isolated green).

Flake ownership:
- **isaac-1d7x** extended to own `parallel_tool_batches.feature:101` (it previously named only `:124`)
- **isaac-w4km** (new draft) owns `turn_queue.feature:69`

Do not retag unverified. Do not hail work or verify on this bean. Do not recut `run-handler`. Registry pin 0.1.59 + ACP 0.1.13 stands.

## Fallout fixed (planner, 2026-09-10)

Agent CI was red after landing: `features/session/parallel_tool_batches.feature` 'cancel mid-batch' asserted the in-flight anchor call reports `tool-result`; with `{:error :cancelled}` preserved it now reports `tool-cancel`, which is the behaviour this bean wanted for ACP. Recut on main (f4813d9), released in agent 0.1.60. The verifier's gate here was the registry spec only; the full feature suite would have caught it.
