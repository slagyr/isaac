---
# isaac-fi41
title: Human escalation must HALT the bean, not just notify
status: todo
type: bug
priority: high
created_at: 2026-07-13T19:03:46Z
updated_at: 2026-07-13T19:03:46Z
---

## Bug (Micah, 2026-07-13, from the isaac-7l5m thrash)

When the verify loop or planner escalates to a human, the notification fires but the delivery loop KEEPS RUNNING — work/verify handoffs, limbo continuations, and dead-letter/re-hail churn all continue. Micah: "I got a call for help from the planner but the bean still churns. This cannot happen. The work needs to stop."

Escalation-to-human is currently advisory (a comm post). It must be a HALT: once a bean is escalated to a human, no crew re-picks it and no continuation re-queues until a human explicitly resumes it.

## Observed

isaac-7l5m escalated to planner (qpp4 flow, 2 verify-fails) AND to human, yet kept thrashing through repeated continuations and 4+ dead-letters afterward. Required manual emergency stop: moving 13 delivery records + 2 turn markers to a PAUSED dir and setting the bean to draft.

## Design

- On human escalation, transition the bean/thread to a HELD state that BOTH the delivery worker and the limbo detector honor: no bind, no continuation, no re-hail.
- Needs a real held/paused status or a per-bean hold marker the worker checks before binding. NOTE: "blocked" is NOT a valid bean status today (valid = in-progress / todo / draft / completed / scrapped). Either add a held status or a hold flag/marker file.
- A human resumes EXPLICITLY (a command such as `isaac hail resume BEAN`, or re-promotion). Nothing auto-resumes.
- The escalation notification should state the bean is HELD and how to resume.
- Interplay: this supersedes je45 for held beans — the limbo detector must not re-queue a held bean.

## Scenarios (worker writes; required coverage)

1. A bean escalated to human enters held: the delivery worker does NOT bind it next tick; the limbo detector does NOT re-queue it.
2. A held bean stays held across ticks until an explicit resume.
3. Explicit resume returns it to the normal loop.
4. Escalation comm content names the held state and the resume path.

## Priority

HIGH — safety/runaway-control gap: without it, a stuck bean burns compute and floods notifications indefinitely despite asking for help.

## Related

Limbo-continuation notifications are too noisy (a rotating-arrow post per continuation) — separate je45 tuning bean: go quiet on routine continuations, shout only on dead-letter/escalation.
