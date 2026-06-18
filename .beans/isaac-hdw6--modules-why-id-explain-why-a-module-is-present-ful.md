---
# isaac-hdw6
title: 'modules why <id>: explain why a module is present (full reverse-dep set)'
status: scrapped
type: feature
priority: normal
created_at: 2026-06-18T20:00:38Z
updated_at: 2026-06-18T21:15:22Z
---

Drill-down companion to `modules list` (the resolved tree, isaac-0yp1). The list
table truncates REQUIRED BY to "first +N"; `modules why <id>` prints the FULL
reason a module is present — every installed module that (transitively) requires
it. Prior art: npm explain, brew uses, apt rdepends.

## Behavior

• `isaac modules why <id>` -> for an EXPLICIT module: "explicitly installed".
  For an IMPLIED module: the full set/chain of requirers (which installed module
  pulled it, via what path). --edn/--json structured form too.
• Unknown id -> friendly error, exit 1.

## Relationships

• Companion to isaac-0yp1 (list-as-tree + REQUIRED BY column; same resolved
  graph, just the untruncated reverse view). Build after 0yp1's resolution
  machinery exists.
