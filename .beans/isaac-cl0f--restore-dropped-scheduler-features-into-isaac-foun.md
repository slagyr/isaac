---
# isaac-cl0f
title: Restore dropped scheduler features into isaac-foundation
status: in-progress
type: task
priority: normal
created_at: 2026-06-15T16:52:43Z
updated_at: 2026-06-15T16:58:00Z
---

Three scheduler feature files dropped. Scheduler machinery is foundation (src/isaac/scheduler/{runtime,cron}.clj),
so restore into isaac-foundation (confirm vs cron if any are cron-driven). Bring step-defs + helpers, remove @wip.

Baseline (@ 09795481):
- features/scheduler/policies.feature  — "Scheduler per-task policies"
- features/scheduler/registry.feature  — "Scheduler task registry"
- features/scheduler/triggers.feature  — "Scheduler trigger firing"
(scheduler/lifecycle.feature already landed — not in scope.)
Target: isaac-foundation/features/scheduler/...

Acceptance: all three green under bb features, no @wip/pending, real behavior (no faked steps)."
