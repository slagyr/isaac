---
# isaac-vfg8
title: 'Cycle timing: log where the gap between a tool batch and the next request goes'
status: in-progress
type: task
priority: high
tags:
    - agent
    - performance
    - unverified
created_at: 2026-09-15T17:12:15Z
updated_at: 2026-09-15T17:41:28Z
---

## Problem

Between a tool batch finishing and the next model request, isaac spends a median 2.3s per cycle (p90 11s, max 80s) with nothing logged inside that gap. Measured on zanebot, `isaac-work-1`, 2026-09-15 15:39–16:06Z, 77 requests: `tool/result → session/compaction-check` median 1.9s (435s total), `compaction-check → chat/stream-request` median 0.4s. That was ~479s of a 26-minute stretch (~30%).

Code reading suggests where it goes, but nothing is measured:
- `persist-tool-call!` / `persist-tool-result!` append each call and result to the session file (`drive/turn.clj` ~180–200).
- The `after-tools` hook calls `maybe-mid-turn-compact!` (`drive/turn.clj` ~1127), which calls `compaction/estimate-prompt-tokens` three times per batch: `before`, again inside `run-compaction-check!`, and `after`. Each one reads the current transcript segment (`policy/get-transcript` → `impl-common/read-transcript-raw`, the whole `current.ednl`, ~650 KB for work-1) and builds the full prompt (`prompt-builder/build`) just to count chars/4.
- `log-token-drift!` (`drive/turn.clj` ~314) reads the active transcript again after every response to sum per-entry `:tokens`.

## Goal

Timing logs that account for every step of the gap, so the running-tally redesign (and any other fix) is driven by numbers.

## Proposal

Debug-level events with `:elapsed-ms` (plus sizes where cheap: entry count, chars) for:
- persisting a tool call and a tool result;
- each transcript read (`read-transcript-raw`: path, bytes, entries, ms);
- each `estimate-prompt-tokens` (transcript load ms, prompt build ms, count ms, and which caller: before / check / after / overflow);
- `run-compaction-check!` total;
- `log-token-drift!`;
- building the next request (`followup-messages`) and the after-tools hook total.

Follow the logging skill (`:domain/action` keywords, debug level, no noise at info).

## Scenarios

Scenario approved 2026-09-15 (Micah). Committed `@wip` in isaac-agent `515a40b`; no new steps.

`features/session/cycle_timing.feature` (new file)
- `:12` one tool batch logs the elapsed time of each step before the next request

Event contract the scenario pins (all debug level, each with `:elapsed-ms`):
`:tool/call-persisted`, `:tool/result-persisted`, `:session/transcript-read` (also `:entries`, `:bytes`), `:session/token-estimate` with `:caller` `:before` / `:check` / `:after`, `:turn/followup-built`, `:turn/after-tools`. The spec layer may add `:overflow` as a caller and a `log-token-drift!` timing; the scenario does not require them.

At landing: remove `@wip` from `:12`.

## Acceptance

```
ISAAC_GIT=1 bb features features/session/cycle_timing.feature
bb ci
```

After deploy (evidence, not a gate for verify): for one `isaac-work-1` cycle on zanebot, the timing events between `tool/result` and the next `chat/stream-request` sum to within ~10% of that gap. Record the per-step numbers on this bean; isaac-4erp uses them as its before-picture.


## Verification handoff

Worker: grok @ work-3
Branch: isaac-agent `bean/isaac-vfg8` @ `0c4684d0d29b8d95eff5d76f8c32570a75ce0d21`
@wip removed from features/session/cycle_timing.feature:12

Acceptance:
  ISAAC_GIT=1 bb features features/session/cycle_timing.feature
  bb ci
  → spec 1610/0, features 755/0/1 pending (unrelated compaction_mid_turn @wip)

Debug events: :tool/call-persisted, :tool/result-persisted, :session/transcript-read
(:path :entries :bytes :elapsed-ms), :session/token-estimate (:caller :before/:check/:after
plus :overflow), :turn/followup-built (logged after estimates so the scenario subsequence
matches; elapsed-ms is the followup-fn wall time), :turn/after-tools, :session/token-drift
:elapsed-ms.

Zanebot gap evidence is still post-deploy, not a verify gate.
