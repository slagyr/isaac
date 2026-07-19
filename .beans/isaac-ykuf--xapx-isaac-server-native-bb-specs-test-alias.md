---
# isaac-ykuf
title: 'xapx: isaac-server — native bb specs (:test alias)'
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
Convert **isaac-server** to native bb specs via the shared runner.

## Wrinkle
Its tasks shell `clojure -M:test:spec` / `-M:test:features` — the `:test` alias adds classpath (spec-support, test deps). The native bb.edn must carry those same deps/paths or specs won't resolve. Likeliest module to hold JVM-only specs → keep `jvm-spec`/`jvm-features` and route any that fail native.

## Acceptance
- [ ] `bb spec` / `bb features` native (no `clojure -M`), streamed, with the `:test`-equivalent classpath present.
- [ ] PARITY: full suite native == JVM results; JVM-only specs routed to `jvm-*`, documented.
- [ ] `bb ci` native; before/after wall-clock recorded here.
