---
# isaac-fi41
title: Human escalation must HALT the bean, not just notify
status: completed
type: bug
priority: high
created_at: 2026-07-13T19:03:46Z
updated_at: 2026-07-19T16:52:38Z
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

## Summary of Changes (2026-07-19)

**Original code-halt design is obsolete — superseded by the fgo0 hail cleanup.**
When this bug was filed (2026-07-13) the thrash it describes (limbo continuations
+ dead-letter/re-hail churn after escalation) was driven by machinery that fgo0
then deleted: hail became pure transport, and the limbo detector (je45) plus both
continuation branches were removed. Post-fgo0 an escalated bean no longer churns —
it simply strands. So the 'held status the delivery worker + limbo detector honor'
design is moot (there is no limbo detector, no continuation re-queue to halt), and
building a code-enforced hold would pull us into the deliberately-PARKED
deterministic-supervisor layer (det-eventbus). We did NOT build that.

**What was actually done (the two remaining prompt-layer pieces):**

1. **Escalation is terminal** — added an explicit rule at all three human-escalation
   sites (hail-bean-plan = canonical owner; hail-bean-work + hail-bean-verify
   continuation-cap fallbacks): once the 🆘 comms are sent the turn is over — no
   handoff, no self-continuation, no re-hail. Prompt-driven convergence, no code
   backstop.
2. **Held marker for visibility** — the escalating role appends a
   `## Held (awaiting human, <date>)` note to the bean body (blocker + 'resumes
   only on explicit human action') and commits/pushes it, so an escalated bean is
   legibly distinct from a bean under active work instead of a silent in-progress
   strand. Nothing auto-resumes; a human resumes explicitly (re-hail / re-promote).

Asserted in test/human-needed.md (escalation sends no further hail; `## Held` note
present; bean ends in-progress + held).

**Deploy:** committed to slagyr/orchestration main (93c72f2), pulled on zanebot,
skills copied into ~/.isaac/prompts/skills/ (picked up on next skill load; no
restart).
