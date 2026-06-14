---
# isaac-fkxv
title: 'Foundation public API: designed Tier-1 + isaac.foundation facade'
status: in-progress
type: feature
priority: normal
created_at: 2026-06-14T02:47:09Z
updated_at: 2026-06-14T02:50:09Z
parent: isaac-brth
---

## Goal

Give the foundation a small, documented, **enforced** public API that every module
depends on — not a flat bag of ~30 namespaces. The clean API comes from three
things: a *designed* Tier-1 surface, a *facade* (`isaac.foundation`) for the
re-exportable part, and an *enforcement* check. The renames are supporting hygiene,
not the headline.

Design context: plan `~/.claude/plans/snoopy-zooming-graham.md` → "Foundation API
surface"; review notes `/tmp/foundation-api-cleanup.md` (claims verified; the
`isaac.shell` one was wrong — see Notes).

## Approach

- **isaac-first.** Do all of this in the isaac repo (canonical source; its
  1962-spec / 745-feature suite catches regressions foundation's 451/41 would
  miss). **Resync to isaac-foundation as a batch at the end** (final step), not
  per-step.
- **Serial, not parallel.** A facade named `isaac.foundation` is a convergence
  point: the design gates everything, the facade references the *final* namespace
  names (so it comes after the renames), and the renames collide on shared files
  (boundary spec, manifest). One agent, one step at a time; **review after each
  step**, with a hard review gate after step 1 (the design) before any renames.

## Facade nuance (decided in step 1)

`isaac.foundation` re-exports the fn/protocol/var surface (`module.protocol`'s
`Module`/`module`, `config.api` read fns, `config.runtime/Reconfigurable`,
`nexus/get`). It CANNOT re-export `cli.api`'s multimethods — a module `defmethod`s
onto the real var — so those stay a documented **direct import**. Step 1 produces
the exact re-export list + carve-outs.

## Steps (one at a time; each ends green under `bb ci`, reviewed before the next)

- [x] **1. Design Tier-1 + `isaac.foundation` facade shape** (design gate — NO
      code). Inventory the module-author surface; classify each member as
      fn/protocol/var (facade-able) vs multimethod/macro (direct import); produce
      the concrete `isaac.foundation` re-export list + direct-import carve-outs +
      a `FOUNDATION.md` tier outline. Present for review.
      → `/tmp/isaac-fkxv-step1-design.md` (2026-06-14). **Awaiting Micah review
      before step 2.**
- [ ] **2. `:isaac.core` -> `:isaac.foundation`.** Rename the module id
      (`src/isaac-manifest.edn`) and the namespace; `isaac.foundation` owns
      `create-module` + manifest constants; retire `isaac.core` and the loader's
      `core-module-id` naming; update `version.clj`'s hardcoded `:isaac.core` and
      the `foundation_boundary_spec` set. Acceptance: `bb ci` green.
- [ ] **3. `isaac.root` -> `isaac.config.root`; dedupe `default-root`.** Bootstrap
      sibling to `paths`/`nav` (requires only fs/logger, NOT config.loader).
      Collapse the three `default-root` shims (`root`, `config.paths`,
      `config.api`) to one. Move `extract-root-flag` (pure CLI arg-stripping) to
      the CLI. Acceptance: `bb ci` green; single `default-root`.
- [ ] **4. `isaac.version` -> `isaac.foundation.version`** (after step 2). Move
      the ns; update refs. Acceptance: `bb ci` green; `isaac --version` works.
- [ ] **5. Build the `isaac.foundation` facade** (per step-1 design). Acceptance:
      a smoke spec — a module-style ns builds a tool/comm/cli command touching
      only Tier-1 (facade + documented carve-outs).
- [ ] **6. Delete the `isaac.configurator` shim.** Confirmed thin re-export;
      callers use `isaac.config.runtime/Reconfigurable`. Acceptance: ns gone,
      `bb ci` green.
- [ ] **7. Trim the nexus schema docs to foundation slots** (`:fs`, `:config`,
      `:module-index`, `:scheduler`); drop platform-wide slots from foundation's
      nexus documentation.
- [ ] **8. `FOUNDATION.md`: document the API tiers** (Tier 1 / 2 / 3 + the facade
      + the direct-import carve-outs), from step 1's outline.
- [ ] **9. Enforcement: module->foundation-internal boundary check.** A spec that
      flags a module requiring a foundation *internal* (`module.loader`,
      `config.loader`, `schema-compose`, `check-compose`, `validation`, ...)
      instead of Tier-1. Acceptance: passes today; a deliberate violation fails it.
- [ ] **10. Resync to isaac-foundation** (batch) + `bb ci` green there.

## Notes

- **No `isaac.util.*`:** there is no `isaac.shell` namespace today (the review
  assumed one) — don't pre-build a util grab-bag.
- `isaac.naming` stays in foundation short-term (agent-leaning; revisit at the
  agent split).
- `isaac.api` (the agent public surface) is NOT touched here — it moves with the
  agent split, a later horizon.
