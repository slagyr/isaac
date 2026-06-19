---
# isaac-yi82
title: modules list warns about module version conflicts
status: todo
type: feature
priority: normal
created_at: 2026-06-19T15:44:56Z
updated_at: 2026-06-19T15:45:09Z
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
