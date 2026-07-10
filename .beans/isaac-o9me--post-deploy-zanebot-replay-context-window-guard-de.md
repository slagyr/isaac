---
# isaac-o9me
title: 'Post-deploy: zanebot replay context-window guard defers hails (isaac-dark rollout)'
status: completed
type: task
priority: normal
created_at: 2026-07-09T19:29:28Z
updated_at: 2026-07-10T16:38:42Z
---



## Sign-off (planner, 2026-07-10)

Replayed live on zanebot post-deploy (agent 0.1.13, hail 0.1.11, discord 0.1.10):
wedged tiny-window session (compaction-disabled, >100% of window) -> hail
deferred `:reason :context-exhausted` retry-after 300s, zero attempts -> attention
enqueued to comm outbox -> `:comm.delivery/delivered` to discord channel isaac.
The replay also flushed out and fixed two integration bugs en route:
isaac-bmgo (discord generic :target) and isaac-88ol (OIDC form encoding).
Test debris removed (o9me-test crew/model, sessions unwedged).
