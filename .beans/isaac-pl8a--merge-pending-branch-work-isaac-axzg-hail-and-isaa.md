---
# isaac-pl8a
title: 'Merge pending branch work: isaac-axzg (hail) and isaac-nfch server-side (cli-server), reconciled'
status: in-progress
type: task
priority: normal
tags:
    - unverified
created_at: 2026-07-07T20:36:38Z
updated_at: 2026-07-07T21:10:46Z
---

Two verified implementations are stranded on branches. (1) isaac-hail origin/isaac-axzg-undeliverable-warn (a1d976a) diverged from main at 0ab4104 — main has since gained the turn-resume/delivery scenarios and an @wip axzg scenario at features/router.feature:395 that the branch implements; expect a router.feature conflict, resolve in favor of the branch (it made the scenario pass), prove suites green under pinned siblings, push. (2) isaac-cli-server origin/isaac-nfch-color-hint conflicts with the merged isaac-iouj logging work in src/isaac_cli_server/dispatch.clj + dispatch_spec.clj — reconcile (both touch the spawn/dispatch path; logging and color-env-hint must coexist), suites green, push. Do NOT re-verify the beans — both passed; this is integration only. Note merge commits here; planner cuts the deploy train after.

## Implementation Notes

- `isaac-hail`: merged `origin/isaac-axzg-undeliverable-warn` into the local integration branch, kept the branch-side `router.feature` scenario resolution, verified `bb spec spec/isaac/hail/router_spec.clj` and `bb ci`, then fast-forwarded `main` and pushed commit `50bb7bb`.
- `isaac-cli-server`: integrated `origin/isaac-nfch-color-hint` with the current logging work, then absorbed the newer `origin/main` endpoint-fixture fix so the FORCE_COLOR change and CI-safe handler stubs coexist. Verified `bb spec spec/isaac/cli_server/dispatch_spec.clj`, `bb spec`, and `bb features`, then pushed merge commit `ed87157` on `main`.
- No planner conflict found; ready for verification handoff.
