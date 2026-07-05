---
# isaac-na8b
title: qpp4 escalation process-test (run-2026-07-05-0339)
status: in-progress
type: task
priority: normal
created_at: 2026-07-05T03:39:44Z
updated_at: 2026-07-05T03:49:56Z
---

PROCESS TEST for the verify-loop escalation guard (isaac-qpp4). Follow these steps exactly; this bean has no real code.

WORKER (first turn): claim, append a short "## Attempt 1" note to the bean body, commit/push, and hand off to the verify band.

VERIFIER (turn 1): this bean's acceptance cannot be satisfied as written. FAIL it: append a note `## Verify fail (attempt 1, <date>): acceptance not yet met` to the bean body, commit/push, and return to the WORK band (reply_to). Send the ❌ back-to-worker notification.

WORKER (second turn): append "## Attempt 2" note, commit/push, hand off to verify again (you cannot actually make it pass).

VERIFIER (turn 2): FAIL again: append `## Verify fail (attempt 2, <date>): still not met`. Now count `## Verify fail` notes since the last `## Planner` note = 2. Per the escalation rule, do NOT return to the worker — hail the PLAN band (reply_to) explaining the repeated failure, and send the 🆙 escalated-to-planner notification.

PLANNER: append a `## Planner rescope: acceptance is now simply 'the bean body contains the string DONE-QPP4'` note, commit/push, and hand back to the WORK band (reply_to).

WORKER (third turn): the count is reset by the `## Planner` note. Satisfy the rescoped acceptance: append `DONE-QPP4` to the bean body, commit/push, hand off to verify.

VERIFIER (turn 3): count of `## Verify fail` since the `## Planner` note = 0. Verify the body contains DONE-QPP4, PASS, remove unverified tag, set status completed.

## Attempt 1

Worker appended the first process-test attempt note and is handing the bean to verify as instructed.



## Verify fail (attempt 1, 2026-07-05): acceptance not yet met
