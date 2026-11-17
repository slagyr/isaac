---
# isaac-hkfw
title: 'A suspended subagent child frees its slot: cancel works, and boot fails orphans'
status: todo
type: bug
priority: critical
created_at: 2026-11-17T14:38:08Z
updated_at: 2026-11-17T14:39:03Z
---

Found 2026-11-17 by Zane Lab (PROPOSALS.md, "cancelling a suspended subagent
doesn't free its slot") and confirmed from zanebot's server log and turn
records.

## What happened

The server was stopped twice with children in flight (05:09:29Z and
05:32:33Z on 2026-11-17; cause is isaac-hqb6). Each time
`:bridge/suspend-starting` listed the children, each child turn ended
`:ended-by :suspended`, and its durable turn record was written
`:state :suspended` (`tool/subagent.clj` `on-turn-ended`, and
`turn/worker.clj:175`). At the next boot `:resume/scan-complete` reported
`:markers 1 :requeued 0`: the one marker was a weather-parked cron session.
None of the 7, then 16, suspended children had a turn marker, so nothing
resumed them.

From then on:

- `subagent__spawn` counts every child whose record is not `:finished`
  (`unfinished?`), so the 16 suspended records filled all 16 slots:
  `subagent limit reached (16 running)`.
- `subagent__cancel` on one of them answered "Child cancellation requested."
  and changed nothing: `cancel-tool` handles `:queued/:held/:waiting-session`
  directly and sends everything else to `cancellation/cancel!`, which has no
  in-flight turn to cancel.

The parent sat blocked for seven hours until the records were edited by hand
(`:state :finished, :outcome :cancelled`; originals in
`~/.isaac-turn-backup-20261117/` on zanebot).

## Design

1. **Cancel works on a suspended child.** `subagent__cancel` treats
   `:suspended` like the queued states: the record becomes
   `:finished`/`:cancelled`, the parent gets its "Child cancelled." turn, and
   any turn marker the child's session still holds is cleared so it cannot
   resume later.
2. **Boot never leaves a dead slot.** As part of
   `resume-interrupted-turns!`, after the marker scan: every subagent turn
   record still `:suspended` or `:running` whose session has no turn marker
   is orphaned. Finish it as failed, log `:subagent/orphaned` at warn with
   parent, session and turn id, and tell the parent through the child's
   observer (`on-turn-died`, reason `interrupted by a restart`), so the
   parent hears "Child failed: interrupted by a restart" and can start it
   again. A child that does have a marker is left to the normal resume.

## Not in this bean

- Why a shutdown-suspended child leaves no turn marker, and resuming such a
  child so it carries on with its work. That is isaac-o1yh (draft).
- Non-subagent turn records left `:suspended` with no marker (one cron turn
  was, `00d47f07`); they hold no slot.

## Acceptance

- The two `@wip` scenarios baselined below pass with `@wip` removed, both in
  `features/tool/subagent_control.feature`.
- One new step, and only this one:
  `the child turn is left suspended by a shutdown`: the single child turn of
  the current session has its durable turn record set to `:state :suspended`
  (as `on-turn-ended` writes it for a suspended result), and its session has
  no turn marker. Nothing else about the record changes.
- `interrupted turns are resumed at "…"` is an existing step; it calls
  `resume-interrupted-turns!`, which is where part 2 runs.
- Every existing scenario in `subagent_control`, `subagents`,
  `bridge/suspend` and `bridge/weather_suspend` passes unchanged.
- `bb ci` and `bb jvm-spec` pass. No version bump; that happens at deploy.
- If a baselined step cannot do what a scenario asks of it, stop and say
  which step; do not bend the production code to fit.

feature-baseline: isaac-agent 8d8cc11e69971fa97684e7f967a98a71cebe6132
feature-blob: isaac-agent features/tool/subagent_control.feature 9304a6e5e5af772791ef1c7151774e5ec52e7486 165,195
