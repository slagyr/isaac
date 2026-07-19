---
# isaac-fvzo
title: 'xapx: isaac-discord — native bb specs (dev-local switch)'
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
Convert **isaac-discord** to native bb specs via the shared runner.

## Wrinkle
Its tasks compute an alias from a `dev-local?` switch (`:dev-local:spec` vs `:spec`). Preserve that behavior — the native path must still honor the dev-local dep set (e.g. env/flag-gated deps) so local vs CI runs stay equivalent.

## Acceptance
- [ ] `bb spec` / `bb features` native (no `clojure -M`), streamed; dev-local behavior preserved.
- [ ] PARITY: full suite native == JVM results; JVM-only specs routed to `jvm-*`.
- [ ] `bb ci` native; before/after wall-clock recorded here.
