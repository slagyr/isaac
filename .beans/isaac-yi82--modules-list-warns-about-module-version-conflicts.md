---
# isaac-yi82
title: modules list warns about module version conflicts
status: in-progress
type: feature
priority: normal
tags:
    - unverified
created_at: 2026-06-19T15:44:56Z
updated_at: 2026-06-19T16:15:45Z
blocked_by:
    - isaac-92p3
---

Once the launcher resolves modules in one unified basis (isaac-92p3), tools.deps
picks ONE version per lib but may have mediated a CONFLICT (module A pinned the
shared dep at v1, module B at v2). Micah: these conflicts will show up almost
immediately, and the user needs to SEE them, not have them silently resolved.

## Behavior

• `modules list` surfaces a WARNING for each module version conflict the resolve
  mediated: which module, the requested versions, and the version CHOSEN.
  e.g. "⚠ isaac.agent: marigold.app wants <v1>, marigold.app2 wants <v2> — using
  <chosen>".
• --edn/--json carry it structurally: e.g. top-level :conflicts [{:id ...
  :requested [...] :chosen ...}] (untruncated).
• No conflict -> no warning (quiet by default).

## Acceptance (feature-test)

• Two configured modules pinning DIFFERENT versions of a shared module ->
  `modules list` prints a conflict warning naming the module + versions +
  chosen; --edn has :conflicts with the same data.
• No-conflict config -> no warning.

## Relationships

• Depends on isaac-92p3 (the unified resolution produces the conflict data this
  surfaces).
• Extends the dhzy `modules list` surface (+ 0yp1's resolved tree).


## Feature -> features/module/conflict_warning.feature (@wip, 3 scenarios)

1. human `modules list` -> a separate CONFLICTS table on stdout (below the main
   list); stdout contains "version conflict" + the module + both versions.
2. `modules list --edn` -> :conflicts structured (id + chosen; full requested
   set untruncated).
3. no conflict -> no conflicts table ("version conflict" absent).

Reuses 92p3's conflict fixtures (marigold.app.conflict / app2.conflict ->
marigold.shared 1.0.0 vs 9.9.9; 1.0.0 chosen).

## Human output layout (decided 2026-06-19 with Micah)

Warnings render as a SEPARATE table below the modules list (NOT stderr lines),
one row PER REQUESTED VERSION so dropped versions + their requirers are visible;
a LOADED ✓ marks the chosen one. Block omitted entirely when there are no
conflicts. --edn/--json stay clean machine output with :conflicts.

  ID                      STATUS  REQUIRED BY                            COORD
  marigold.app.conflict   ok                                            local modules/marigold.app.conflict
  marigold.app2.conflict  ok                                            local modules/marigold.app2.conflict
  marigold.shared         ok      marigold.app.conflict, app2.conflict  local modules/marigold.shared

  ⚠  1 version conflict — one version loaded; the rest dropped
  MODULE           VERSION  REQUIRED BY              LOADED
  marigold.shared  1.0.0    marigold.app.conflict    ✓
  marigold.shared  9.9.9    marigold.app2.conflict

REQUIRED BY in the conflicts table truncates first +N like the main table.

## :conflicts shape (--edn)

  :conflicts [{:id :marigold.shared
               :chosen "1.0.0"
               :requested [{:version "1.0.0" :required-by [:marigold.app.conflict]}
                           {:version "9.9.9" :required-by [:marigold.app2.conflict]}]}]


## Layout is asserted (not just contents)

conflict_warning.feature scenario 1 pins the conflicts-table LAYOUT with
`the stdout matches:` — each row a regex (re-find over whole stdout, '+' for
spacing): the "N version conflict" header, the "MODULE VERSION REQUIRED BY
LOADED" column row, the loaded row ending in ✓, and the dropped row without ✓.
Robust to column widths; strict on structure + the ✓ marker.



## Handoff

isaac-foundation @ e68a9bb (231be0c + slow-scenario fix)
bb ci green (754 spec + 105 feature examples)
