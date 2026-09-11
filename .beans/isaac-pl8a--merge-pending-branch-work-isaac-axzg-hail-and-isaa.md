---
# isaac-pl8a
title: 'Merge pending branch work: isaac-axzg (hail) and isaac-nfch server-side (cli-server), reconciled'
status: completed
type: task
priority: normal
created_at: 2026-07-07T20:36:38Z
updated_at: 2026-07-07T21:34:21Z
---

Two verified implementations are stranded on branches. (1) isaac-hail origin/isaac-axzg-undeliverable-warn (a1d976a) diverged from main at 0ab4104 — main has since gained the turn-resume/delivery scenarios and an @wip axzg scenario at features/router.feature:395 that the branch implements; expect a router.feature conflict, resolve in favor of the branch (it made the scenario pass), prove suites green under pinned siblings, push. (2) isaac-cli-server origin/isaac-nfch-color-hint conflicts with the merged isaac-iouj logging work in src/isaac_cli_server/dispatch.clj + dispatch_spec.clj — reconcile (both touch the spawn/dispatch path; logging and color-env-hint must coexist), suites green, push. Do NOT re-verify the beans — both passed; this is integration only. Note merge commits here; planner cuts the deploy train after.

## Implementation Notes

- `isaac-hail`: merged `origin/isaac-axzg-undeliverable-warn` into the local integration branch, kept the branch-side `router.feature` scenario resolution, verified `bb spec spec/isaac/hail/router_spec.clj` and `bb ci`, then fast-forwarded `main` and pushed commit `50bb7bb`.
- `isaac-hail` verify follow-up: verifier found the merge had left a stale duplicate `@wip` axzg scenario below the c58s section while the active non-`@wip` scenario already existed earlier in the file. Removed the duplicate `@wip` copy, re-ran `bb spec spec/isaac/hail/router_spec.clj`, `bb features features/router.feature`, and `bb ci`, then pushed fix commit `15edc42` on `main`.
- `isaac-cli-server`: integrated `origin/isaac-nfch-color-hint` with the current logging work, then absorbed the newer `origin/main` endpoint-fixture fix so the FORCE_COLOR change and CI-safe handler stubs coexist. Verified `bb spec spec/isaac/cli_server/dispatch_spec.clj`, `bb spec`, and `bb features`, then pushed merge commit `ed87157` on `main`.
- No planner conflict found; ready for verification handoff.


## Verify fail (attempt 1, 2026-07-07): isaac-hail integration left the axzg acceptance scenario @wip

Evidence:
- `features/router.feature` still contains the original `@wip` axzg scenario at lines 423+.
- The merge also added a second non-`@wip` copy earlier at lines 336+ instead of resolving the conflict in favor of the branch version.
- `bb features features/router.feature:336` passes, but `bb ci` still excludes the lingering `@wip` scenario, so the integration does not satisfy the bean's requirement to land the passing scenario on main.
- `isaac-cli-server` integration at `ed87157` verified green; the fail is isolated to `isaac-hail`.
