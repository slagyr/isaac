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

## Verify fail resume (attempt 1) — worker assessment

Verifier is correct that the **full gate is not green**. Diagnosis:

### isaac-hooks — 2 pending features (NOT a runner gap)
- `features/auth_migration.feature` — "Old :hooks :auth :token slot fails validation pointing to the new slot"
- `features/config_validate.feature` — "validate reports unknown model refs with file and valid set"

These scenarios have **no step implementations** (gherclj "not yet implemented"). They were already pending under the pre-xapx JVM `bb features` path. Making them green requires product work (retired-field schema + validate messaging steps), not bb.test-tasks wiring. Options: (a) implement the steps + schema behavior in a follow-up bean, or (b) tag `@wip` / drop from acceptance for this runner-conversion bean.

### isaac-imessage — lifecycle failures + features gate
- Lifecycle scenarios fail under **both** native and JVM on current main (config hot-reload surface vs monorepo/split pin age).
- Additional feature files still reference missing step vars (`imessage-chat-db-responds-with-rows`, etc.) — steps/features out of sync on main **before** this bean.
- `bb ci` currently omits features deliberately because features are red on main; wiring them into ci without fixing product will keep main red.

### Recommendation
This bean is a **runner conversion** (xapx child). Forcing product feature completion inside it expands scope beyond "native bb specs via shared runner." Returning to planner to either:
1. Rescope acceptance: cron done; hooks ok with pending tagged `@wip` or explicitly out-of-scope; imessage ci = native+jvm specs until a product bean fixes lifecycle/steps.
2. Or spawn child beans for hooks pending scenarios + imessage lifecycle/step parity, keep dt9h blocked on those.

Worker will not mark unverified again until scope is clarified.

