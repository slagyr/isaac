---
# isaac-7ivl
title: 'xapx: isaac-acp — native bb specs (JVM-alias/JVM-only deps)'
status: todo
type: task
created_at: 2026-07-19T17:10:52Z
updated_at: 2026-07-19T17:10:52Z
parent: isaac-xapx
blocked_by:
    - isaac-x5ru
---

Parent: isaac-xapx. Blocked-by the shared-runner re-home (xapx child 1).

## Goal
Convert **isaac-acp** to native bb specs via the shared runner.

## Wrinkle
Its bb.edn comment says tests run via JVM clojure aliases "matching" deps.edn — a hint it may lean on JVM-only deps. Investigate first: try native and see what fails. Anything genuinely JVM-only stays on `jvm-spec`/`jvm-features` and is documented; the rest goes native.

## Acceptance
- [ ] `bb spec` / `bb features` native for the specs that CAN run native (no `clojure -M`), streamed.
- [ ] PARITY: native + `jvm-*` together == the old JVM results; JVM-only specs documented, not dropped.
- [ ] `bb ci` native path; before/after wall-clock recorded here.
