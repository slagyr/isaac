---
# isaac-flgy
title: Split isaac.config.loader by responsibility (pure refactor)
status: in-progress
type: task
priority: normal
tags:
    - isaac-foundation
    - refactor
created_at: 2026-08-03T14:13:12Z
updated_at: 2026-08-03T14:42:10Z
---

## Description

Pure refactor — no new behavior. `isaac.config.loader` in **isaac-foundation** is 1,025 lines / ~95 defs covering eight responsibilities. Split it by responsibility and move the specs alongside, keeping 1:1 src↔spec correspondence.

Sibling of isaac-1tce (module loader split); same principle: split the production module, tests follow — never spec-only splits.

## Proposed namespaces

(line ranges from current config/loader.clj)

- `isaac.config.env` — env-override atom, `.env` reading/locking, `env` lookup (25–68). The `${VAR}` value source.
- `isaac.config.parse` — EDN/YAML/frontmatter parsing, `${VAR}` substitution walk, file-read primitives (72–130).
- `isaac.config.companions` — inline-vs-`.md` companion resolution: souls, cron prompts, hook templates, hail prompts (238–298).
- `isaac.config.entities` — entity-dir scanning, overlay machinery, frontmatter entities, schema finalize/merge, dangling-`.md` warnings (177–230, 451–630).
- `isaac.config.normalize` — all `normalize-*` (defaults/compaction, crew, model, provider, cron) + `present-config-keys` (137–175, 632–723).
- `isaac.config.warnings` — top-level/root-entity/unknown-key/table warnings (299–330, 725–769) — or fold into existing `isaac.config.validation` if the fit is real; worker decides.
- `isaac.config.loader` — stays, reduced to what the name promises: `read-root-config` → `validate-root-config` → `load-config-result` orchestration, berth-slice conformance, compose-or-fallback (331–450, 770–926).
- Snapshot/state tail (927–1025): snapshot atom + `set-snapshot!` / `load-config!`; `root` / `resolve-workspace` / `read-workspace-file` likely belong in existing `isaac.config.root` (pointer/root location owner). Fold there if it fits; only invent `isaac.config.state` if it doesn't.

## Constraints

- Clean cutover — callers re-point directly; no back-compat aliases or re-export shims. `load-config-result` has ~10 call sites; the rest are 1–2 each.
- Layering must stay a DAG: `parse` → `env`; `entities`/`companions` → `parse`; `loader` orchestrates. Verify `conform-berth-slices`' dependency on `schema_compose` doesn't introduce a cycle.
- All new namespaces stay under `isaac.config.*` so the config-bypass lint's sanctioned-exception list is unaffected.

## Acceptance Criteria

1. No behavior change: full `bb ci` green (specs + features) with no test rewrites — existing examples move, they don't change meaning.
2. Every new `src/` namespace has a corresponding `spec/` file (1:1 maintained).
3. No namespace exceeds ~500 lines; `isaac.config.loader` reduced to load orchestration.
4. No back-compat aliases or re-export shims remain.
5. SCRAP on the resulting spec files: no file rated MANUAL_SPLIT (config/loader_spec.clj is currently HIGH 40.5 / MANUAL_SPLIT).

## Work notes / conflict (scrapper@isaac-work-1)

Split implemented in foundation on branch work (not yet final-pushed pending cutover decision):

**New src ns (all under isaac.config.*):**
- env, parse, companions, entities, normalize, warnings
- loader reduced to orchestration + snapshot/workspace (~378 lines)

**Specs:** 1:1 smoke/moved describes for new ns; loader_spec split so no MANUAL_SPLIT remains.

**Conflict with acceptance “no re-export shims” + “bb ci green”:**
Downstream **isaac-agent** (and agent-spec gitlib) call:
- `isaac.config.loader/normalize-config`
- `isaac.config.loader/env`
(and related) extensively. Clean cutover requires re-pointing those call sites in **other repos**, which this bean’s foundation-only scope does not cover. Without either (a) allowed thin re-exports on loader, or (b) scoped follow-up beans to re-point agent (+ other modules), `bb ci` fails analysis when agent code loads (`Unable to resolve symbol: loader/normalize-config`).

**Ask planner:** expand bean / add follow-ups to update downstream callers, **or** amend acceptance to allow temporary public re-exports on loader for the former public surface (`normalize-config`, `env`, `clear-env-overrides!`, `set-env-override!`).
