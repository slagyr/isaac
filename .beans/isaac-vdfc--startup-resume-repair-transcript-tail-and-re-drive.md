---
# isaac-vdfc
title: 'Startup resume: repair transcript tail and re-drive interrupted turns (generalize 0tf3)'
status: in-progress
type: feature
priority: normal
created_at: 2026-07-06T15:44:51Z
updated_at: 2026-07-06T19:04:43Z
parent: isaac-wq8m
blocked_by:
    - isaac-7li9
---


## Gap

isaac-0tf3 recovery re-drives orphaned HAIL deliveries only; interactive and cron turns are never resumed. And even hail re-delivery re-drives against whatever transcript tail the dead turn left behind — if the turn died between an assistant tool-call message and its tool results, the appended re-delivery message produces the malformed structure providers reject (`No tool call found for function call output` — the isaac-63f3/0h7b family).

## Proposed change

One unified startup resume path, keyed off the durable turn markers (isaac-7li9):

1. On startup, scan surviving turn markers — each is an interrupted turn with its resume routing.
2. **Repair the transcript tail to a clean boundary** before re-driving: unresolved trailing tool calls get synthesized tool-results ("interrupted before execution / result unknown — re-verify before repeating side effects") or are truncated to the last clean entry. Repair is recorded in the transcript (like a compaction marker), never silently.
3. Re-drive by source: hail-sourced markers reschedule the delivery (attempts++, backoff, dead-letter budget intact — absorbing `recover-orphaned-inflight!`); comm/cron-sourced markers re-drive the turn from the saved charge routing and deliver the reply on the original comm.
4. Markers flagged `interrupted` at a clean boundary (graceful drain, sibling bean) resume unambiguously; unmarked survivors (hard crash) get the full repair treatment.

## Notes

- Replaces isaac-0tf3's recovery as a special case; there must be exactly ONE re-drive path so hail turns cannot be double-driven.
- Mid-tool ambiguity is irreducible on hard crash (did the `git push` land?): the synthesized tool-result must instruct the model to verify side effects rather than blindly repeat them.

## Design (2026-07-06, Micah-approved)

- **F1 — Resume pricing.** `:suspended` marker (clean deploy) → re-queue with attempts UNCHANGED (suspend is planned, not evidence). Unstamped orphan (hard crash) → attempts+1: if a poison turn crashed the server, dead-letter budget is the only crash-loop breaker. Crash is evidence.
- **F2 — Per source.** `:hail` → write the embedded delivery back to deliveries/, delete marker, normal worker delivers (redelivery re-appends the hail prompt over partial work — existing post-0tf3 semantics). `:comm`/`:cron` → dispatch a resume charge whose prompt is an interruption note ("interrupted by a restart; continue from the transcript"); a user message after trailing toolResults is structurally valid on all providers.
- **F3 — Transcript repair (durable + logged, never silent).** Dangling toolCalls (assistant msg with calls, no results) → synthesize a toolResult per call: "Interrupted before/during execution; result unknown — verify side effects before repeating." Kills the orphaned-tool-result provider rejection (isaac-63f3/0h7b family). Torn trailing JSONL line (crash mid-append) → truncate to last complete line, log what was dropped.
- **F4 — Ordering.** Resume scan runs at startup BEFORE the delivery worker's first tick. Per marker: re-queue/dispatch FIRST, delete marker SECOND — crash between leaves both, which converges (stale-delivery dedup removes the stray; marker resumes next boot). Reverse order + crash = lost turn.
- **F5 — Comm staleness.** `:turn-resume-window-ms` default 600000, measured from `:interrupted-at` (suspend stamp), fallback `:started-at` (crash). Inside → resume with note; outside → drop marker, log `:resume/comm-stale`. Hail and cron never go stale — they are work orders.

## Scenarios (approved one-by-one, 2026-07-06)

Committed @wip:
- isaac-hail `features/turn-resume.feature` (commit e3fb5c3): suspended re-queue attempts-intact (line 22), crash orphan attempts+1 (line 56), dangling-toolCall repair before re-queue (line 86).
- isaac-agent `features/session/resume_repair.feature` (commit 825e97e): torn-line truncation + fresh-comm resume-with-note (line 17), stale-comm drop (line 48).

New machinery (approved): step `interrupted turns are resumed at "<instant>"` (pinned now; registered in BOTH suites); step `session "..." has a torn trailing transcript line "..."` (raw bytes, no newline — agent suite); log events `:resume/crash-orphan` (warn), `:resume/transcript-repair` (warn, with `repair` field :dangling-tool-call | :torn-line), `:resume/comm-stale` (info); config `:turn-resume-window-ms` default 600000.

This feature REPLACES isaac-0tf3's inflight orphan recovery — when this lands with isaac-7li9, delivery.feature's 0tf3 scenario pair and `recover-orphaned-inflight!` are removed (verdicts recorded on isaac-7li9).

## Acceptance

- [ ] `bb features features/turn-resume.feature:22` green (isaac-hail)
- [ ] `bb features features/turn-resume.feature:56` green (isaac-hail)
- [ ] `bb features features/turn-resume.feature:86` green (isaac-hail)
- [ ] `bb features features/session/resume_repair.feature:17` green (isaac-agent)
- [ ] `bb features features/session/resume_repair.feature:48` green (isaac-agent)
- [ ] Resume scan wired into server startup BEFORE the delivery worker's first tick
- [ ] recover-orphaned-inflight! and the 0tf3 scenario pair removed (with isaac-7li9)
- [ ] Full suites green in both repos; @wip removed from all five scenarios
