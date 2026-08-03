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
