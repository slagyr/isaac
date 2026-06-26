---
# isaac-fkxv
title: 'Foundation public API: designed Tier-1 + isaac.foundation facade'
status: completed
type: feature
priority: normal
tags: []
created_at: 2026-06-14T02:47:09Z
updated_at: 2026-06-26T20:10:04Z
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

## Facade nuance (decided in step 1; adjusted after steps 3 + 5)

`isaac.foundation` re-exports the fn/protocol/var surface (`module.protocol`'s
`Module`/`module`, `config.api` read fns, `isaac.reconfigurable/Reconfigurable`,
`nexus/get`). It CANNOT re-export `cli.api`'s multimethods — a module `defmethod`s
onto the real var — so those stay a documented **direct import**.

**Carryovers for step 6** (step-1 design inputs shifted by steps 3 + 5):

1. **`default-root` — drop from facade.** Step 1 listed it under `config.api`; step 3
   moved the single `default-root` to `isaac.config.root` and removed it from
   `config.api`. Bootstrap/CLI concern, not module-author surface → **direct import**
   via `isaac.config.root` (already in carve-outs). Do not re-export from facade.
2. **`Reconfigurable` — source `isaac.reconfigurable`.** Step 1 correction (Micah
   review): not `config.runtime` (Tier-2 host) or `config.berths` (internal). Step 5
   landed `isaac.reconfigurable`; facade re-exports from there.

**Out of facade (unchanged):** `isaac.foundation.version` — distribution concern only.

## Steps (one at a time; each ends green under `bb ci`, reviewed before the next)

- [x] **1. Design Tier-1 + `isaac.foundation` facade shape** (design gate — NO
      code). Inventory the module-author surface; classify each member as
      fn/protocol/var (facade-able) vs multimethod/macro (direct import); produce
      the concrete `isaac.foundation` re-export list + direct-import carve-outs +
      a `FOUNDATION.md` tier outline. Present for review.
      → `/tmp/isaac-fkxv-step1-design.md` (2026-06-14). **Reviewed:** structure and
      classification approved; `Reconfigurable` → `isaac.reconfigurable` (not
      `config.runtime`). Gate cleared for step 2.
- [x] **2. `:isaac.core` -> `:isaac.foundation`.** Rename the module id
      (`src/isaac-manifest.edn`) and the namespace; `isaac.foundation` owns
      `create-module` + manifest constants; retire `isaac.core` and the loader's
      `foundation-module-id` / `foundation-index` naming; update `version.clj`'s
      hardcoded `:isaac.foundation` and the `foundation_boundary_spec` set.
      Acceptance: `bb ci` green (1962 spec / 745 feature).
- [x] **3. `isaac.root` -> `isaac.config.root`; dedupe `default-root`.** Bootstrap
      sibling to `paths`/`nav` (requires only fs/logger, NOT config.loader).
      Collapse the three `default-root` shims (`root`, `config.paths`,
      `config.api`) to one in `isaac.config.root/default-root`. Move
      `extract-root-flag` to `isaac.cli.args`. Acceptance: `bb ci` green
      (1968 spec / 745 feature); single `default-root` defn.
- [x] **4. `isaac.version` -> `isaac.foundation.version`** (after step 2). Move
      the ns; update refs. Acceptance: `bb ci` green; `isaac --version` works.
- [x] **5. Extract `Reconfigurable` to `isaac.reconfigurable`.** Foundation-public
      protocol home (mirrors `module.protocol`). Move `defprotocol Reconfigurable` +
      invoke helpers out of `config.berths`; `berths` / `configurator` / `runtime`
      alias from the new ns. Repoint module implementors (`marigold.longwave`, comm,
      hail, hooks, cron). Delete `isaac.configurator` shim. Acceptance: `bb ci`
      green (1968 spec / 745 feature); no module requires `berths` or `configurator`
      for the protocol.
- [x] **6. Build the `isaac.foundation` facade** (step-1 design + carryovers above).
      Re-export: `module.protocol` (`Module`, `module`, `module?`); `config.api`
      read fns **without** `default-root` (`load-config!`, `load-config-result`,
      `snapshot`, `root`, `normalize-config`, `env`); `isaac.reconfigurable`
      (`Reconfigurable`, `on-startup!`, `on-config-change!`); `nexus` (`get`,
      `get-in`, `register!`). `create-module` stays in this ns (step 2). Acceptance:
      smoke spec — module-style ns touches only facade + documented carve-outs
      (`cli.api`, `config.root` for bootstrap, etc.).
- [x] **7. Trim the nexus schema docs to foundation slots** (`:fs`, `:config`,
      `:module-index`, `:scheduler`); drop platform-wide slots from foundation's
      nexus documentation.
- [x] **8. `FOUNDATION.md`: document the API tiers** (Tier 1 / 2 / 3 + the facade
      + the direct-import carve-outs), from step 1's outline.
- [x] **9. Enforcement: module->foundation-internal boundary check.** A spec that
      flags a module requiring a foundation *internal* (`module.loader`,
      `config.loader`, `schema-compose`, `check-compose`, `validation`, ...)
      instead of Tier-1. Acceptance: passes today; a deliberate violation fails it.
- [x] **10. Resync to isaac-foundation** (batch) + `bb ci` green there.
      Post-resync patch (isaac `202e6fbe`, foundation follow-up): removed
      `marigold.bridge.cli` `requiring-resolve` evasion of step-9 boundary
      enforcement — `longwave-ping` now contributes via `:isaac/cli` +
      `isaac.cli.api/run` in both repos. `bb ci` green (isaac 1981/745;
      foundation 465/41).

## Notes

- **No `isaac.util.*`:** there is no `isaac.shell` namespace today (the review
  assumed one) — don't pre-build a util grab-bag.
- `isaac.naming` stays in foundation short-term (agent-leaning; revisit at the
  agent split).
- `isaac.api` (the agent public surface) is NOT touched here — it moves with the
  agent split, a later horizon.
