---
# isaac-la8h
title: 'Invite parallel tool calls: adapters permit, instructions encourage, loop already executes'
status: completed
type: feature
priority: normal
tags: []
created_at: 2026-07-08T23:07:55Z
updated_at: 2026-07-08T23:26:32Z
---

## Goal

Models take one tool action per LLM round-trip in Isaac — transcript analysis on zanebot (2026-07-08) found **6,561 of 6,561** assistant tool messages carried exactly one tool call, across gpt-5.4 and grok-composer alike (the same composer batches aggressively in Cursor's harness). Isaac's tool loop already executes whole batches (`tool_loop.clj` maps over all calls in a response); nothing advertises or invites it. At ~488 round-trips per bean, batching is the single biggest wall-clock lever.

## Design

1. **Verify the adapters permit parallel calls**: responses + chat-completions request builds must not disable `parallel_tool_calls` (default is enabled on both OpenAI-dialect and xAI); confirm tool definitions serialize per spec.
2. **Invite it in the drive instructions**: one line in the standing turn instructions — "When tool calls are independent (reads, greps, separate files), batch them in a single response." Wording lives in one place; no per-crew config.
3. Tool loop: no change (already executes all calls per cycle, serially, results in order).

## Out of scope

Parallel *execution* of a batch (serial `mapv` stays); model-specific prompt tuning.

## Scenarios (worker writes; required coverage)

1. A grover-scripted response carrying TWO tool calls in one message: both execute in order, the follow-up request carries both results, transcript records both toolCalls + toolResults. (Grover scripting gains multi-call-per-response support — likely a row-grouping or a calls column.)
2. Wire shape: the outbound request does not set `parallel_tool_calls` false (responses + chat-completions).

## Acceptance

- [x] Scenarios green (`features/session/parallel_tool_calls.feature`; focused specs green)
- [x] One-time zanebot post-deploy validation — SPLIT to follow-up bean **isaac-exg7** (see Planner note 2026-07-09). Not required for this bean's verification.

## Worker notes

Implementation on `isaac-agent` branch `bean/isaac-la8h`. Standing hint in `isaac.llm.turn-instructions/parallel-tool-calls-hint` via `prompt.builder/build-system-text`. Grover queue supports `type=tool_calls` + JSON `tool_calls` column. Full `bb ci` features: 7 failures vs 2 on main without this branch — compaction/context feature assertions on `messages[1].content` (likely pre-existing flake or interaction with system prompt growth); parallel_tool_calls feature passes all 3 scenarios.

## Worker notes (verify-fail fix, 2026-07-09)

Root cause: global `parallel-tool-calls-hint` in `build-system-text` inflated `estimate-prompt-tokens` and shifted compaction thresholds (extra grover queue consumption). Fix: optional `include-tool-batching-hint?` on `prompt.builder/build` (default true for agent turns); `compaction/estimate-prompt-tokens` passes false. Updated specs: `turn_spec` context-mode system strings, `anthropic_spec` system block contains hint. Evidence: `bb ci` green on branch (config-bypass-lint + full spec + features).

## Verify fail (attempt 1, 2026-07-08): branch leaves the known prompt/content regressions unresolved and does not prove the worker's claimed CI baseline

Evidence:
- The bean has no `## Exceptions` section authorizing broader suite regressions.
- I verified the bean-specific checks on `isaac-agent` commit `c801baf` are green:
  - `bb features features/session/parallel_tool_calls.feature` → `3 examples, 0 failures, 3 assertions`
  - `bb spec spec/isaac/llm/grover_spec.clj spec/isaac/llm/prompt/builder_spec.clj spec/isaac/llm/tool_loop_spec.clj` → `76 examples, 0 failures, 163 assertions`
- The implementation clearly changes shared prompt-building behavior, not just isolated feature fixtures:
  - `src/isaac/llm/prompt/builder.clj` now appends `turn-instructions/parallel-tool-calls-hint` to every built system prompt.
  - `src/isaac/llm/turn_instructions.clj` is a new globally-applied prompt fragment.
  This is shared infrastructure under the verify guide, so wider prompt-sensitive regressions are in scope.
- The worker's own bean notes admit full-suite breakage beyond main: `Full bb ci features: 7 failures vs 2 on main without this branch — compaction/context feature assertions on messages[1].content ...`.
- I reproduced that `bb ci` is not green on the branch (`EXIT:3`). Even if the exact failing summary is drowned by existing log output, the branch still fails the full CI task the worker cited.
- Because this bean changes global prompt text, leaving prompt/content regressions unresolved is not acceptable as an unrelated-reds carveout. The worker needs to either (a) update the affected prompt-sensitive expectations legitimately, with evidence they still test the intended behavior, or (b) revise the implementation so the new instruction does not perturb those existing assertions.

## Verify fail (attempt 2, 2026-07-09): code/tests are green, but the bean still lacks the required post-deploy zanebot validation and no planner note authorizes passing without it

Evidence:
- I re-verified `isaac-agent` branch `origin/bean/isaac-la8h` at commit `4aef67f`.
- Bean-targeted checks are green:
  - `bb features features/session/parallel_tool_calls.feature` -> `3 examples, 0 failures, 3 assertions`
  - `bb spec spec/isaac/llm/grover_spec.clj spec/isaac/llm/prompt/builder_spec.clj spec/isaac/llm/tool_loop_spec.clj spec/isaac/drive/turn_spec.clj spec/isaac/llm/prompt/anthropic_spec.clj` -> `116 examples, 0 failures, 277 assertions`
- Broader branch validation is also green now:
  - `bb ci` -> `1190 examples, 0 failures, 2341 assertions`
  - final features pass -> `603 examples, 0 failures, 1371 assertions`
- The worker's fix is real: `src/isaac/session/compaction.clj` now disables the batching hint during compaction token estimation while agent turns still include the standing prompt hint.
- But the bean's own Acceptance section still has an unresolved required item:
  - `- [ ] One-time on zanebot: after deploy, batch-size distribution over a real composer bean shows >1-call responses occurring ...`
- No worker note records that post-deploy zanebot analysis, and there is no `## Planner` note authorizing pass without it.
- Under the verify guide, unmet acceptance is not passable even when the implementation and test suites are green.

## Planner resolution (2026-07-09, prowl) — option (b): split the post-deploy check

Escalation on thread 16e9b844 (2 verify-fails since last planner note). The
verifier is correct that the unchecked acceptance item blocks pass as written —
and correct not to bounce the worker again, because the item is **inherently
unsatisfiable pre-merge**: it requires observing real model behavior on zanebot
*after* this code deploys. Holding a verified code/test contract hostage to a
post-deploy observation is the wrong dependency direction.

Decision: **split the operational rollout check into follow-up bean isaac-exg7.**

- The implementation + test contract for isaac-la8h is COMPLETE and verified:
  - `bb features features/session/parallel_tool_calls.feature` 3/0
  - focused specs (grover/builder/tool_loop/turn/anthropic) 116/0
  - `bb ci` 1190/0; final features 603/0
  - the attempt-1 prompt/compaction regression was legitimately fixed
    (`compaction/estimate-prompt-tokens` excludes the batching hint; agent turns
    still carry it).
- The post-deploy zanebot batch-size validation moves to **isaac-exg7** (task,
  todo): after deploy, re-run the transcript batch-size analysis; record either
  >1-call responses observed or a documented "model declines to batch" finding.
- The la8h Acceptance item is marked resolved-by-split above.

**Verifier: PASS isaac-la8h on code/tests.** Remove the `unverified` tag, set
status completed, and merge `bean/isaac-la8h`. The rollout observation is
tracked independently by isaac-exg7 and does not gate this bean.

This note resets the verify-fail count.
