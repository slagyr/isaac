---
# isaac-nquj
title: 'isaac-server: drop its hand-rolled comm-kinds filter'
status: draft
type: task
priority: low
created_at: 2026-09-30T00:46:06Z
updated_at: 2026-09-30T00:46:06Z
---

Follow-up from isaac-3y69 (2026-09-29). isaac-server's `isaac.http.module/comm-kinds` hand-rolls the same `:configurable? false` filter foundation's `isaac.config.comm-kinds` had, reading agent's `:isaac.agent/comm` berth by name. isaac-3y69 deleted foundation's copy: `isaac.schema.registered-in`'s `:known` now excludes `:configurable? false` contributions generically (display only).

## Wanted

isaac-server stops hand-rolling it: use the generic registered-in mechanism (or whatever foundation now exposes) wherever it lists comm kinds, and delete its copy. Clean cutover.

## Acceptance

Scenarios TBD by the planner: whatever isaac-server surface lists comm kinds keeps listing only configurable ones. Existing server scenarios stay green.

## Likely repo scope

isaac-server (+ foundation pin bump if it needs a newer foundation).
