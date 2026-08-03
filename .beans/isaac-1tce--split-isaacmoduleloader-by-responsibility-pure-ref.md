---
# isaac-1tce
title: Split isaac.module.loader by responsibility (pure refactor)
status: in-progress
type: task
priority: normal
tags:
    - isaac-foundation
    - refactor
    - unverified
created_at: 2026-08-03T14:11:00Z
updated_at: 2026-08-03T15:34:44Z
---

## Description

Pure refactor — no new behavior. `isaac.module.loader` in **isaac-foundation** is 1,523 lines / ~120 top-level defs, but only 14 are used outside the namespace (12 caller files). Split it by responsibility and move the specs alongside, restoring 1:1 src↔spec correspondence.

Principle (Micah): too many tests means the module has too many responsibilities — split the production module, tests follow. Do NOT split spec files on their own.

## Proposed namespaces

Responsibility clusters as they sit in the current source (line ranges from pre-split loader.clj):

- `isaac.module.coords` — module identity & coordinates (65–205): id/lib-sym conversion, split-repo aliases, gitlibs + local/root directory resolution.
- `isaac.module.classpath` — classpath mutation (208–310): dynamic classloader, `invoke-add-deps!`, dedupe/preload planning, loaded-coord tracking. Owns the dynamic vars `*resolve-classpath?*`, `*planned-classpath-pairs*`, `*skip-preload-planned?*` (bound externally by `isaac.startup.classpath-cache`).
- `isaac.module.discovery` — manifest discovery (315–630, 1203–1322): resource scanning, transitive requirements, `discover!`, cycle errors, `builtin-index` (9 external call sites — the workhorse).
- `isaac.module.berths` — berth processing (1074–1201): `process-manifest-berths!` (already comment-fenced, isaac-8yxs lineage).
- `isaac.module.lifecycle` — activation & lifecycle (632–1073): `activate!`, `load-modules!`, rollback, `reconcile-modules!`, shutdown/start, boot-stats, handler-registry injection (25–64).
- `isaac.module.versions` — version divergences (1323–1447): version parse/compare, conflict-vs-drift classification.
- `isaac.module.loader` — dissolves or shrinks to boot orchestration (`discover!` → `load-modules!` flow) + config composition (1448–1523: `compose-config-modules!`, `config->launch-deps`), or those move too and loader disappears. Worker decides during the refactor; clean cutover either way.

## Constraints

- Clean cutover — callers re-point to the new namespaces directly; no back-compat aliases, no façade re-exports. ~12 files of mechanical require changes.
- Exact boundary between discovery and classpath planning needs care: they are intertwined via the preload machinery (`plan-module-classpath-pairs`, `preload-planned-module-deps!`).
- Specs split along the same lines (the 2026-08-02 spec-only split, reverted in 0d16423, previews the layout: discovery / berths / activation / compose). Reuse that decomposition where it fits the final namespaces.

## Acceptance Criteria

1. No behavior change: full `bb ci` green (specs + features) with no test rewrites — existing examples move, they don't change meaning.
2. Every new `src/` namespace has a corresponding `spec/` file (1:1 restored).
3. No namespace exceeds ~500 lines; `isaac.module.loader` either gone or reduced to orchestration.
4. No back-compat aliases or re-export shims remain.
5. SCRAP on the resulting spec files: no file rated MANUAL_SPLIT.

## Implementation notes (scrapper@isaac-work-2)

- Split into: coords, classpath, discovery, lifecycle, berths, versions; loader owns list/compose/launch-deps.
- Foundation callers rewired to owning namespaces (no foundation-internal loader usage for split APIs).
- **Loader still forwards** the public fn surface (`register-handler!`, `discover!`, `activate!`, …) for published agent/server/hail/… modules that still require `isaac.module.loader`. Without those forwards, loading the live ~/.isaac config NPEs during `check-compose` when agent checks require the old path. Dynamics remain dual-declared (classpath owns the real bindings foundation uses). Follow-up: cut over external modules, then delete forwards (AC #4 full clean cutover).
- SCI fix: `check_compose` resolves `:fn` via `get` / descriptor lookup — never `{:keys [fn]}`.
- Specs: `loader_discovery` / `loader_berths` / `loader_activation` / `loader_compose` / `lifecycle_spec` + fixtures. SCRAP: HIGH/REVIEW_FIRST on some files, **no MANUAL_SPLIT**.
- Verified: `bb spec` 838/0, `bb features` 133/0 on branch `bean/isaac-1tce` @ `10fe7df`.

## Verify fail (attempt 1, 2026-08-03): acceptance unmet — AC #2 and AC #4 are still false on `isaac-foundation` `10fe7df`

Evidence from verify on `isaac-foundation` commit `10fe7df`:
- `bb ci` ✅ `838 examples, 0 failures, 1484 assertions` and `133 examples, 0 failures, 314 assertions`
- `bb scrap spec/isaac/module` ✅ no file rated `MANUAL_SPLIT`
- AC #4 still fails: `src/isaac/module/loader.clj` keeps back-compat forwards/re-exports (`register-handler!`, `discover!`, `activate!`, `load-modules!`, `reconcile-modules!`, etc.) at lines 26–58
- AC #2 still fails: the new split source namespaces (`coords`, `classpath`, `discovery`, `berths`, `versions`) do not have matching 1:1 spec files; the current split specs remain `loader_discovery_spec.clj`, `loader_berths_spec.clj`, `loader_activation_spec.clj`, `loader_compose_spec.clj`, and `lifecycle_spec.clj`

This bean cannot pass until the implementation matches the written acceptance, or the planner explicitly rescopes/updates the acceptance for the deferred external-module cutover and spec-file mapping.

## Verify-fail / conflict (scrapper@isaac-work-2, 2026-08-03)

Verifier correctly flagged AC #2 and AC #4 as unmet on 10fe7df.

Attempted remediation this turn:
1. **AC #2 specs**: renamed/split to 1:1 — `coords_spec`, `classpath_spec`, `discovery_spec`, `berths_spec`, `versions_spec`, `lifecycle_spec` (merged activation), `loader_spec` (compose/list only). Module-suite specs green (89/0).
2. **AC #4 no shims**: removed loader forwards again; rewired **local** `isaac-agent` + `isaac-server` src to owning namespaces.

**Blocker (cannot satisfy AC #4 and AC #1 together on this machine without a multi-repo release):**
- Live `~/.isaac` pins agent/server/… at **git SHAs** whose published source still calls `isaac.module.loader/{register-handler!,activate!,builtin-index,…}`.
- Feature suite / CLI paths that resolve the real root load that config, compose pulls those SHAs onto the bb classpath, and ns-load NPEs without loader forwards (35 feature failures). Soft/hard check-compose handling cannot avoid `requiring-resolve` pulling old agent code.
- Local agent/server checkouts were rewired but are **not** what config compose loads (gitlibs SHAs are).

**Options for planner:**
- **A (recommended):** Rescope AC #4 to “no foundation-internal shims; external-module cutover + SHA bump is a follow-up bean (or multi-module PR set)”; keep thin loader forwards until then. AC #2 met by the renamed specs.
- **B:** Expand this bean (or spawn linked beans) to cut over agent/server/hail/… , publish SHAs, bump `~/.isaac` + CI pins, then delete forwards — true clean cutover.
- **C:** Weaken AC #1 for environments with live multi-module roots (not realistic).

Local WIP on foundation branch may include AC #2 renames + partial resilience (main_spec home isolation, read-user-config Throwable catch, clear-loaded-coords in clear-activations!, SCI check_compose). Agent/server local mains have uncommitted rewires — leave for option B or revert if A.

