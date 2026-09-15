---
# isaac-vfg8
title: 'Cycle timing: log where the gap between a tool batch and the next request goes'
status: draft
type: task
priority: high
tags:
    - agent
    - performance
created_at: 2026-09-15T17:12:15Z
updated_at: 2026-09-15T17:12:15Z
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

## Acceptance (draft — scenarios TBD)

- A feature shows one driven tool batch producing timing events for each step above with `:elapsed-ms`.
- On zanebot after deploy: the events for one `isaac-work-1` cycle sum to within ~10% of the measured `tool/result → chat/stream-request` gap.
