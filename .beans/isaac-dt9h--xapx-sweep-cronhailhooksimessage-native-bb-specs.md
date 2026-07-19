---
# isaac-dt9h
title: 'xapx: sweep cron/hooks/imessage — native bb specs'
status: todo
type: task
priority: normal
created_at: 2026-07-19T17:10:51Z
updated_at: 2026-07-19T17:38:37Z
parent: isaac-xapx
blocked_by:
    - isaac-x5ru
---

Parent: isaac-xapx. Blocked-by the shared-runner re-home (xapx child 1).

## Goal
Convert the four **plain** module repos — **isaac-cron, isaac-hail, isaac-hooks, isaac-imessage** — to native bb specs via the shared `bb.test-tasks` runner. Batched because their bb.edn test tasks are identical (`shell clojure -M:spec` / `-M:features`, no wrinkles).

## Do (per repo)
- Replace `spec`/`features`/`ci` with `tests/run-spec!` / `run-features!` / `run-ci!` + `jvm-spec`/`jvm-features` fallbacks.
- Wire bb.edn deps/paths for native speclj/gherclj/test-support.

## Acceptance (per repo — all four)
- [ ] `bb spec` / `bb features` native (no `clojure -M`), streamed.
- [ ] PARITY: full suite native == JVM results; JVM-only specs routed to `jvm-*`.
- [ ] `bb ci` native; before/after wall-clock recorded here per repo.

## Scope update (2026-07-19)

**isaac-hail is DONE** — it was x5ru's pilot consumer, converted and merged to hail main (`a520a4f`). This sweep now covers **cron / hooks / imessage** only. Pin `isaac-foundation-test-support` `:git/sha` = `43cf46e00087bf066a9e065ccc3d48dd2814ac23` (foundation main). Use isaac-hail's merged bb.edn as the reference pattern.
