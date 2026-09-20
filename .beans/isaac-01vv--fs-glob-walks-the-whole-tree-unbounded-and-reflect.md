---
# isaac-01vv
title: 'fs__glob walks the whole tree unbounded and reflects per path: prune, budget, and type-hint it'
status: in-progress
type: bug
priority: high
tags:
    - tools
    - performance
created_at: 2026-09-20T18:41:53Z
updated_at: 2026-09-20T20:21:50Z
---

Repo: **isaac-agent**. Found 2026-09-20 on zanebot: a boot-resumed heartbeat turn called `fs__glob` with `{:pattern "**/heartbeat-state.json" :path "/Users/zane"}` and ground for **three minutes** at 100% of a core before finding the file at `~/.openclaw/workspace/memory/heartbeat-state.json`. Because boot runs resumed turns synchronously (isaac-yxch), the whole server — HTTP listener, hail delivery, every component — waited on that one glob.

## Why it is this slow

`isaac.tool.glob/glob-candidates` does `Files/walk` over the entire root and **materializes every regular file into a vector** before any matching. Under `/Users/zane` that is `.gitlibs`, `.m2`, `Library`, `node_modules`, every git object — hundreds of thousands of entries, none of which can match. Then `normalize-relative-path` calls `.toString` on an untyped local, so every single path pays a reflective method lookup (the thread dump caught it inside `Reflector.invokeMatchingMethod` → `Class.getMethods`).

Nothing bounds the walk: no entry budget, no wall clock, no pruning. `head_limit` only trims the *result* after the whole tree has been read.

## Change

1. **Prune during the walk.** Skip these subtrees by default: `.git`, `.gitlibs`, `.m2`, `.cpcache`, `node_modules`, `target`, `Library`. If the caller's pattern or path names one explicitly, search it — the skip is a default, not a prohibition.
2. **Bound the scan.** A default entry budget (suggest 20k, configurable) and a wall-clock budget (suggest 5s). On exceeding either, return the matches found so far plus a line saying the scan budget stopped it and how far it got, the same shape as today's truncation line.
3. **Kill the reflection.** Type-hint the path so `normalize-relative-path` and the matcher path stop reflecting.
4. Keep today's semantics when the scan completes inside budget: mtime-desc sort, `head_limit`, and the existing truncation message.

## Scenarios (committed `@wip` on isaac-agent main 24aca81, `features/tool/glob.feature`)

| scenario | asserts |
|---|---|
| glob skips heavy directories by default | files under `node_modules`, `.git`, `.gitlibs` are not returned; `src/core.clj` is |
| glob searches a heavy directory when the pattern names it | `node_modules/**/*.clj` finds the file inside it |
| glob stops at the scan budget and says how far it got | with a budget of 2 entries the result is not an error and contains "scan budget" |

## Step ledger

| step | status |
|---|---|
| a clean test directory … / the following files exist: / the tool "fs__glob" is called with: / the tool result is not an error / the tool result lines match: / the tool result contains … / does not contain … | reuse |
| **the glob scan budget is `<n>` entries** | **NEW** — binds the budget for one scenario |

## Acceptance

`@wip` removed and

```
cd isaac-agent && bb features features/tool/glob.feature && bb spec spec/isaac/tool/glob_spec.clj && bb ci
```

One-time check, not a scenario: on a tree the size of a home directory the call returns inside the wall-clock budget instead of running for minutes.

feature-baseline: isaac-agent 24aca81ef34848ee8ca5ac9fa8d84e5c5d230e55
feature-blob: isaac-agent features/tool/glob.feature 3da4f4a81ead8c72c38942ba5c8106a11d53bebc

Dispatched: hail f7071963 2026-09-20T18:44Z (band isaac-work)

Dispatched: hail 62739f9f 2026-09-20T20:17:50Z (band isaac-work)

## Landed on main (2026-09-20)

main-sha: isaac-agent e948ce35f7cdd7e6322c660a514c43a28e22cf50

Scan is breadth-first with a prune set (`*skip-dirs*`), an entry budget
(`*scan-entry-budget*`, 20k) and a wall-clock budget (`*scan-millis-budget*`,
5s); symlinked directories are not descended. Partial scans append
"Stopped at the scan budget after N entries. Results are partial."
One-time check: `{:pattern "**/heartbeat-state.json" :path "/Users/zane"}`
returned in 1.6s **with the match** (was ~3 minutes before).
