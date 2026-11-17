---
# isaac-z8z4
title: A turn resumed after a crash keeps its observers, so Foreman hears the verdict
status: in-progress
type: bug
priority: critical
tags:
    - unverified
created_at: 2026-11-17T15:14:41Z
updated_at: 2026-11-17T15:23:44Z
---

Repo: **isaac-agent** (`bridge/resume.clj`). The crash path only.

## What is wrong

Foreman attaches an observer to each work and verify turn; that is how the
turn's verdict gets back. When the server dies mid-turn, the turn is resumed
at boot, but the new queue record has no observers: `enqueue-resume-turn!`
copies them only from the turn marker, and the marker never holds them
(`bridge/core.clj` `turn-marker` writes only source, start time and
leases). The interrupted queue record still has them and is already read
there, but only to close it. The resumed turn finishes, nothing hears it,
and the bean sits until someone signals it by hand.

Zane Lab counted 7 of 7 such turns in its sample; four beans waited 0.5 to
2 hours and one sat 458 hours. With these counted, 11 of 29 beans stalled in
24 hours.

## Acceptance

```gherkin
Scenario: the resumed turn's observers hear how it ends
  Given queue record "t-orig" for session "bean-demo" is :running with observers [:lookout]
  And a restart marker for "bean-demo" with no :observers
  When interrupted turns are resumed
  And the record that resumed "t-orig" is dropped
  Then the stdout contains "turn ended (error dropped)"

Scenario: the resumed turn reports under the first request id
  Given queue record "t-orig" is :running with a Foreman observer
  And a restart marker with no :observers
  When interrupted turns are resumed and the queue drives the new record
  Then the charge's :observers equal t-orig's
  And its :turn-id is "t-orig"

Scenario: a second restart keeps the first request id
  Given queue record "t-resume-1" is :running with observers and :observer-turn-id "t-orig"
  When interrupted turns are resumed
  Then the new record's :observer-turn-id is "t-orig"

Scenario: a weather-parked marker still wins (isaac-cpiz)
  Given a weather marker with :observers and :turn-id "child-turn"
  When interrupted turns are resumed
  Then the new record's :observers and :observer-turn-id come from the marker
```

## Not in this bean

- The clean-shutdown path, where the interrupted record is already
  `:suspended` and Foreman reads the suspended reply as a quiet answer.
  The lab's first patch for it broke three cases. It waits until this one
  has run for a few days. **Do not change Foreman here.**
- Resuming a suspended subagent child (isaac-o1yh, draft).
- isaac-hkfw landed in the same file today (boot fails orphaned children).
  Keep its behaviour: a child record this bean can resume is not an orphan.

## Working notes

- Approved by Micah 2026-11-17 from Zane Lab's findings. Ungated: worker,
  then verifier. The scenarios above are the behaviour to prove; write them
  as feature scenarios where the repo's existing steps can express them and
  as specs where they cannot, and say in the bean which is which.
- Reference work on zanebot (read-only): `/Users/zane/agents/zane-lab/experiments/resumed-observer-fix/` (`BEAN.md`, `fix.patch`), `/Users/zane/agents/zane-lab/experiments/stall-postmortem/REVIEW.md`, and `/Users/zane/agents/zane-lab/experiments/graceful-observer/REVIEW.md` for what the wider fix got wrong. Read the experiment's
  `README.md` and `REVIEW.md` first; the reviewer broke the first version
  and says how. The patches were cut against week-old pins and stacked on
  other lab patches, so they will not apply cleanly: take the idea and the
  specs, write the change against today's main. You own the result.
- Do not run the lab's `check.sh` scripts as they are: several kill by
  pattern or by process group. Never signal a process group, and never use
  `pkill` or `killall`.
- `bb ci` and `bb jvm-spec` pass. No module version bump; that happens at
  deploy. A dependency sha pin may move when this bean needs an upstream
  change that has landed.


## Exceptions

Authorized by the planner, 2026-11-17. These feature edits are part of this bean and are not tampering:

- No existing scenario changes. A new `features/turn/resume_observers.feature` is expected.
- Any feature file in this bean's repo: adding new scenarios, and new step definitions for them, that prove the acceptance above. No existing scenario may be removed or weakened beyond what is listed here.


## Implementation and verification (2026-11-17)

isaac-agent bean/isaac-z8z4 commit ded7e0b54983f11ce57318ff00290ff0da0a7bdd. Crash resume now falls back to the interrupted :running queue record for observers and the first request id; weather marker observers remain authoritative. No Foreman or clean-shutdown changes.

Acceptance mapping: the resumed turn drop delivers the lookout verdict in features/turn/resume_observers.feature (real CLI and resume scan); the resumed charge carries the Foreman observers and original turn id, the second restart retains the first id, and the weather marker takes precedence in spec/isaac/agent/bridge/resume_spec.clj. A no-observers guard is also covered there. The existing marker-backed subagent/orphan examples remain passing.

TDD: focused resume specs failed 3/23 and feature failed 1/1 before production change; afterwards focused specs 23/0 and feature 1/0. bb ci: 2041 specs/0 failures, 983 features/0 failures (1 pre-existing pending); bb jvm-spec: 2041/0. bb bean-gate verify isaac-z8z4 --dir isaac-agent=../isaac-agent-isaac-z8z4 exited 2: no feature-baseline. Implementation remains on bean branch pending independent verification.
