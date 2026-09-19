---
# isaac-ro67
title: 'isaac-google: the inbox worker never runs in the server — accepted pushes sit in inbox/pending forever'
status: completed
type: bug
priority: critical
tags:
    - google
created_at: 2026-09-19T23:36:12Z
updated_at: 2026-09-19T23:36:48Z
parent: isaac-bv1l
---

Found 2026-09-19 23:34Z on yopp, first real Google push after Funnel: `:google/push-received` (token verified, record persisted, 204) and then nothing — the record stayed in google/inbox/pending. `isaac.google.worker/tick!` is only driven by the feature step 'the inbox worker ticks'; the runtime component scheduled the registration timer alone.

Fix: the component schedules `:google/inbox` (worker/tick! every 2 s) beside `:google/registration` and cancels both on stop. Spec: start! registers both task ids; stop! cancels both. Version 0.1.6.

The fourth production-only gap in this module today (with isaac-6krg's three). Common cause: the harnesses drive timers by step and stub Google, so the server's own scheduling and Google's real contract were never exercised. A follow-up worth its own bean: a live smoke path (real scheduler, real Google against a test project) before a module ships.

## Acceptance
    cd isaac-google && bb spec spec/isaac/google/component_spec.clj && bb ci   # 50 spec / 19 feature green

## Handoff / resume
branch: bean/google-inbox-worker @ 085d30b (base origin/main@d5dc7b5) — fast-forward. Planner lands it (zanebot out of tokens) and deploys to yopp.



## Landed on main (2026-09-19)
main-sha: isaac-google 2349b111d6cd915de7a620f59e2927408e180bd8
Verified by the planner at Micah's instruction (zanebot out of provider tokens). Registry pinned; deployed to yopp.
