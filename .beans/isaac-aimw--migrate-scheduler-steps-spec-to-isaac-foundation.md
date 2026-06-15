---
# isaac-aimw
title: Migrate scheduler_steps_spec to isaac-foundation
status: in-progress
type: task
priority: low
created_at: 2026-06-15T16:20:01Z
updated_at: 2026-06-15T16:24:03Z
---

Restore the dropped isaac.scheduler-steps-spec (1 example) into isaac-foundation.

Baseline: isaac/spec/isaac/scheduler_steps_spec.clj @ 09795481 (ns isaac.scheduler-steps-spec, 1 it:
          'does not wait for unrelated hung handlers before later assertions poll').
Source:   isaac-foundation/src/isaac/scheduler/{runtime,cron}.clj  (scheduler is foundation-owned, NOT cron)
Target:   isaac-foundation/spec/isaac/scheduler_steps_spec.clj
Acceptance (gate): file(it)==executed, 0 failures, zero '(it) inside (it)', 1 example faithful.
