---
# isaac-2xj5
title: 'Graceful shutdown: suspend in-flight turns at a clean step boundary'
status: in-progress
type: feature
priority: normal
created_at: 2026-07-06T15:44:33Z
updated_at: 2026-07-06T18:51:39Z
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

## Design (2026-07-06, Micah-approved) — vocabulary: SUSPEND

"Suspend" (not "drain" — drain implies letting work finish): stop uncompleted turns at their next step boundary so they can be resumed. Turns are parked mid-stride at a safe footstep, neither finished nor lost.

- **F1 — Suspend = cancel-all + bounded wait.** Reuses the existing cooperative cancellation (bridge/cancellation.clj; tool loop checks at turn.clj:823; LLM streams abort mid-stride opt-in). On stop!, after intake is refused: cancel! every in-flight session, await the in-flight set emptying, bounded by the cap. The current tool/step finishes and persists; the loop stops instead of taking the next step.
- **F2 — Marker disposition.** completed → marker deleted; user-cancelled → deleted (resuming would be wrong); suspended → STAMPED, not deleted: `:suspended true` + `:boundary :clean` (stopped at a step boundary) or `:unclean` (cap expired mid-step). A server-wide suspend flag makes the shared clear path convert delete → stamp. isaac-vdfc resumes clean boundaries directly, repairs unclean ones.
- **F3 — Cap**: config `:server :suspend-timeout-ms`, default 15000 (launchd SIGKILLs ~20s after SIGTERM; leave headroom for the rest of stop!).
- **F4 — A suspended hail turn must not reschedule.** The delivery thread outlives the stopped worker; today a cancelled result would route into reschedule! (attempts++ or dead-letter). Suspend guard: a :suspended result leaves the marker for resume — no reschedule, no delivered/, no attempts. The delivery's fate is decided at next startup by isaac-vdfc.
- **F5 — Placement in stop!**: after delivery-worker/router stop (nothing new starts), before supervisor/module/scheduler teardown (turns still need tools/config). Phase 1.5 of the existing sequence.
- **Limitation (recorded)**: SIGKILL / kill -9 / kernel panic never runs this — that is isaac-vdfc's hard-crash path.

## Scenarios (approved one-by-one, 2026-07-06)

Committed @wip:
- isaac-agent `features/bridge/suspend.feature` (commit 1ada929): suspend stamps :clean (line 17), cancel deletes marker (line 37), cap expiry stamps :unclean (line 52).
- isaac-hail `features/delivery.feature` (commit e599cc4): suspended hail turn keeps marker, no reschedule/attempts (line 548).

New machinery (approved): step `in-flight turns are suspended` + parameterized variant `...with timeout Nms` (registered in BOTH suites); marker stamp keys `:suspended` / `:boundary` (:clean | :unclean); log event `:hail/delivery-suspended` (info); config `:server :suspend-timeout-ms` default 15000. Suspend rides the existing cooperative-cancellation path — a server-wide suspend flag converts the marker clear into a stamp and the turn result into :suspended.

## Acceptance

- [ ] `bb features features/bridge/suspend.feature:17` green (isaac-agent)
- [ ] `bb features features/bridge/suspend.feature:37` green (isaac-agent)
- [ ] `bb features features/bridge/suspend.feature:52` green (isaac-agent)
- [ ] `bb features features/delivery.feature:548` green (isaac-hail)
- [ ] app/stop! (isaac-server) invokes suspend as phase 1.5: after delivery-worker/router stop, before supervisor/module/scheduler teardown
- [ ] :server :suspend-timeout-ms wired, default 15000
- [ ] Full suites green in both repos; @wip removed from all four scenarios
