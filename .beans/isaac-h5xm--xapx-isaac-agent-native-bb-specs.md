---
# isaac-h5xm
title: 'xapx: isaac-agent — native bb specs'
status: todo
type: task
priority: high
created_at: 2026-07-19T17:10:51Z
updated_at: 2026-07-19T17:10:51Z
parent: isaac-xapx
blocked_by:
    - isaac-x5ru
---

Parent: isaac-xapx. Blocked-by the shared-runner re-home (xapx child 1).

## Goal
Convert **isaac-agent** `bb spec` / `bb features` / `bb ci` to run natively in babashka via the shared `bb.test-tasks` runner — no `clojure -M` shell-out, streamed output.

## Current
`bb.edn`: `(apply sh/sh "clojure" "-M:spec" ...)`, `-M:features`, and a `ci` that shells both AND buffers out/err (workers get no live feedback — this is the module that bit us on isaac-n9ez).

## Do
- Replace `spec`/`features`/`ci` tasks with `tests/run-spec!` / `run-features!` / `run-ci!`; add `jvm-spec`/`jvm-features` fallbacks (foundation's shape).
- Wire bb.edn deps/paths so speclj + gherclj + test-support resolve natively.

## Acceptance
- [ ] `bb spec` / `bb features` run native (no `clojure -M`) and STREAM output.
- [ ] PARITY: full spec + non-wip features pass native with the SAME results as the JVM run; any JVM-only spec routed to `jvm-*`, not dropped.
- [ ] `bb ci` uses the native path.
- [ ] Before/after `bb ci` wall-clock recorded here.
