---
# isaac-kcck
title: Companion .md loading reads the table's :companion descriptor (no :crew/:berths in foundation)
status: completed
type: task
priority: normal
created_at: 2026-09-30T04:57:54Z
updated_at: 2026-09-30T06:18:24Z
---

Found by the isaac-mxgn/601n scenario drafting (2026-09-30). Foundation's config-LOAD side of companion markdown files (`isaac.config.companions/companion-md-relative`) is hard-coded to the kinds `:crew` and `:berths`, while the WRITE side (`isaac.config.mutate/companion-spec`) already reads each table's own `:companion` descriptor from its schema. So a module-declared table with a companion field (e.g. cron's `:prompt`) loads its `.md` only by accident of naming, and foundation names agent's `:crew`.

## Wanted

The load side reads the table's `:companion` descriptor from the composed schema, exactly like the write side. No kind names in foundation. Clean cutover.

## Acceptance

- A foundation scenario with a manifest-only Marigold fixture table (unique module id) declaring `:companion {:field :notes :mode :required}` (or the shape the write side uses) loads the companion `.md` into that field on `config get`.
- The 5 companion scenarios the isaac-mxgn/isaac-601n mappings left in isaac-agent move to foundation (worker writes them against a Marigold fixture; the planner reviews at verification) and their agent originals are deleted.
- `grep` of foundation `src/isaac/config/companions.clj` for `:crew`/`:berths` returns nothing. Full CI green in foundation and agent.

## Ungated

Generalization with worker-written scenarios; planner verifies. Hand off `tag=unverified`.


## Landed on main

main-sha: isaac-foundation 0c6e881e4047f650c43d2772dde617aab66f2bd7
main-sha: isaac-agent 13d18c7062e8acbb3ec6cb1f8db61685db53c150
