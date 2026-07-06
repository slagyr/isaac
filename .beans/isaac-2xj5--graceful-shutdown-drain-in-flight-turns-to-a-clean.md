---
# isaac-2xj5
title: 'Graceful shutdown: drain in-flight turns to a clean step boundary'
status: draft
type: feature
priority: normal
created_at: 2026-07-06T15:44:33Z
updated_at: 2026-07-06T15:44:33Z
parent: isaac-wq8m
blocked_by:
    - isaac-7li9
---


## Gap

`app/stop!` (isaac-server) stops delivery workers, supervision, modules, scheduler, and HTTP immediately. It does NOT wait for in-flight turns: a turn is killed wherever it happens to be — including mid-tool (e.g. during a `git push`), which is the one interruption point that is genuinely ambiguous to resume (did the side effect land?). Every deploy currently guarantees this failure mode for any active turn.

## Proposed change

On clean shutdown (SIGTERM / service stop):

1. Stop accepting new turns immediately (refuse dispatch; delivery workers stop claiming — already partially true today).
2. Let each in-flight turn run to its NEXT step boundary — the current model call or tool execution finishes and its transcript entry is durably appended — then interrupt the loop instead of starting the next step.
3. Bound the drain with a hard cap (configurable, default small — deploy/launchd kill windows are unforgiving). A turn that exceeds the cap is interrupted where it is; its marker records that the boundary is unclean.
4. Mark each interrupted turn's durable marker (isaac-7li9) as `interrupted` with the boundary state, so startup resume can distinguish clean-boundary resumes (unambiguous) from mid-step kills (need transcript repair).

## Notes

- This makes the common case (deploy) yield CLEAN boundaries: every tool call either has a persisted result or provably never started. The hard-crash path (sibling resume bean) handles the dirty case.
- Interacts with the tool loop in isaac.drive.turn (`execute-llm-turn!` / `tool-loop/run`): needs an interrupt check between steps.
