---
# isaac-cl0f
title: Restore dropped scheduler features into isaac-foundation
status: completed
type: task
priority: normal
created_at: 2026-06-15T16:52:43Z
updated_at: 2026-06-15T17:01:50Z
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



## Implemented

Repo: isaac-foundation
Files: features/scheduler/{policies,registry,triggers}.feature, spec/isaac/scheduler_steps.clj (full server-free step defs)
Verify: `cd isaac-foundation && git pull --rebase && bb features features/scheduler/`
Result: 16 scheduler scenarios green (72 total features). Full CI: spec 735/0, features 72/0. No @wip.
