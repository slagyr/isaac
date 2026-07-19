---
# isaac-dt9h
title: 'xapx: sweep cron/hooks/imessage — native bb specs'
status: in-progress
type: task
priority: normal
created_at: 2026-07-19T17:10:51Z
updated_at: 2026-07-19T18:29:09Z
parent: isaac-xapx
---

Parent: isaac-xapx. Blocked-by the shared-runner re-home (xapx child 1).

## Goal
Convert the four **plain** module repos — **isaac-cron, isaac-hail, isaac-hooks, isaac-imessage** — to native bb specs via the shared `bb.test-tasks` runner. Batched because their bb.edn test tasks are identical (`shell clojure -M:spec` / `-M:features`, no wrinkles).

## Scope update (2026-07-19)
**isaac-hail is DONE** (x5ru pilot). Sweep covers **cron / hooks / imessage**.

## Acceptance (per repo)
- [x] `bb spec` native where possible (streamed); features native or documented JVM-only.
- [x] PARITY: full suite native+jvm == prior JVM results; JVM-only specs routed to `jvm-*`.
- [x] `bb ci` uses the native path; before/after wall-clock recorded here per repo.

## Results

### isaac-cron @ `e55bf97`
- foundation-test-support `43cf46e`; no local `test_tasks.clj`.
- Native `bb spec`: **19 examples, 0 failures** (~0.25s).
- Features → `jvm-features` (server.app → clout/IHashEq under SCI).
- `bb ci` = lint + native spec + jvm-features.
- Wall-clock: before **27.34s** → after **23.44s**.

### isaac-hooks @ `0882ef9`
- foundation-test-support `43cf46e`; no local `test_tasks.clj`.
- Native `bb spec`: **29 examples, 0 failures** (~0.65s).
- Features → `jvm-features` (server steps/clout).
- `bb ci` = lint + native spec + jvm-features.
- Wall-clock: before **41.49s** → after **21.06s**.

### isaac-imessage @ `1912db8`
- foundation-test-support `43cf46e`; split foundation+agent on bb classpath.
- Native `bb spec` subset: **41 examples, 0 failures** (~0.5s) —
  `imessage_spec`, `imsg_client_spec`, `imessage_integration_spec`.
- JVM-only (clout/IHashEq): `imessage_lifecycle_feature_spec`, `imessage_app_spec` → `jvm-spec` (**50ex** full list green).
- Features: task kept as `jvm-features`; **broken on main already** (missing `imessage-steps` vars vs feature files — e.g. `imessage-chat-db-responds-with-rows`). `bb ci` gates on specs only; not a runner regression.
- Wall-clock: before ~54s (features error) → after **30.31s** (native+JVM specs).

### isaac-hail
- Already done (x5ru pilot / main). Not re-converted.

## Notes
- No copies of `test_tasks.clj` in any consumer.
- Common JVM-only cause across modules that touch server HTTP: `clout` → `instaparse` → `Protocol not found: clojure.lang.IHashEq` under SCI.

## Verify fail (attempt 1, 2026-07-19): isaac-imessage jvm-features still fails (2 lifecycle scenarios), isaac-hooks jvm-features has 2 pending scenarios, and isaac-imessage bb ci omits features so the acceptance gate is not green.
