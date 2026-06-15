---
# isaac-aimw
title: Migrate scheduler_steps_spec to isaac-foundation
status: in-progress
type: task
priority: low
created_at: 2026-06-15T16:20:01Z
updated_at: 2026-06-15T16:30:03Z
---

Restore the dropped isaac.scheduler-steps-spec (1 example) into isaac-foundation.

Baseline: isaac/spec/isaac/scheduler_steps_spec.clj @ 09795481 (ns isaac.scheduler-steps-spec, 1 it:
          'does not wait for unrelated hung handlers before later assertions poll').
Source:   isaac-foundation/src/isaac/scheduler/{runtime,cron}.clj  (scheduler is foundation-owned, NOT cron)
Target:   isaac-foundation/spec/isaac/scheduler_steps_spec.clj
Acceptance (gate): file(it)==executed, 0 failures, zero '(it) inside (it)', 1 example faithful.



## Verification failed

HEAD: 49a935b68c68a9fdb50be3e4ff1aeb93a7aa2814
Working tree: clean

Target file [spec/isaac/scheduler_steps_spec.clj](/Users/micahmartin/agents/verify/isaac-foundation/spec/isaac/scheduler_steps_spec.clj:1) is missing from `isaac-foundation` `main`, so the migration was not actually delivered. `git rev-parse --verify HEAD^{tree}:spec/isaac/scheduler_steps_spec.clj` fails in `isaac-foundation`, and `git log --all -- spec/isaac/scheduler_steps_spec.clj` returns no commits for that path. The baseline example still exists in [isaac/spec/isaac/scheduler_steps_spec.clj](/Users/micahmartin/agents/verify/isaac/spec/isaac/scheduler_steps_spec.clj:1). I stopped before test execution because the acceptance gate `file(it)==executed` already fails at the file-presence step.
