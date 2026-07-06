---
# isaac-vdfc
title: 'Startup resume: repair transcript tail and re-drive interrupted turns (generalize 0tf3)'
status: draft
type: feature
priority: normal
created_at: 2026-07-06T15:44:51Z
updated_at: 2026-07-06T15:44:51Z
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
