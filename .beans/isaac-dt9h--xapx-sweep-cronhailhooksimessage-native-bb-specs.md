---
# isaac-dt9h
title: 'xapx: sweep cron/hail/hooks/imessage — native bb specs'
status: todo
type: task
created_at: 2026-07-19T17:10:51Z
updated_at: 2026-07-19T17:10:51Z
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
