---
# isaac-ou38
title: 'isaac logs formats entries that contain #object tagged literals'
status: todo
type: bug
priority: normal
created_at: 2026-09-05T03:54:22Z
updated_at: 2026-09-05T04:03:34Z
---

Likely repo: **isaac-foundation** (`isaac.log-viewer/format-line`, used by `isaac logs`).

## Problem

`isaac logs` pretty-prints a line only when `clojure.edn/read-string` returns a map. Unknown tagged literals throw, and the viewer prints the raw line — no columns, no color.

Live server.log is full of this: `:tool/start` includes `:progress! #object[fn …]`. The rest of the map is valid EDN. `pr-str` of a function is not an EDN tag `clojure.edn` knows.

Truly garbage lines (`this is not edn`) must still pass through.

## Decision (2026-09-05)

Read log lines with `{:default tagged-literal}` so unknown tags (`#object`, records, etc.) become `TaggedLiteral`s and the enclosing map still formats. Do **not** switch the viewer to fast-edn for this; `clojure.edn` already takes `:default`. `--plain` unchanged. Writer-side stripping of `:progress!` is a separate, better hygiene fix — not this bean.

Existing scenario "Unparseable lines pass through as raw text" stays green.

## Scenario plan (2026-09-05)

1. A log line whose payload contains `#object[…]` still renders time, level, and event columns — **approved**; `@wip` `features/logs/cli.feature:64`
2. A log line with some other unknown tag (not `#object`) still formats the same way — **approved**; `@wip` `features/logs/cli.feature:74`

## Acceptance

```
cd isaac-foundation
bb features features/logs/cli.feature:64
bb features features/logs/cli.feature:74
bb features features/logs/cli.feature:58
bb spec spec/isaac/log_viewer_spec.clj
```

0 failures. Remove `@wip` from those two rows. Existing "Unparseable lines pass through as raw text" (`:58`) stays green.
