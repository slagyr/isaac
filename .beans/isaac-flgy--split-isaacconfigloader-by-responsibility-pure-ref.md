---
# isaac-flgy
title: Split isaac.config.loader by responsibility (pure refactor)
status: completed
type: task
priority: normal
tags:
    - refactor
    - isaac-foundation
created_at: 2026-08-03T14:13:12Z
updated_at: 2026-08-03T22:11:46Z
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
4. **No foundation-internal back-compat aliases or re-export shims.** Temporary
   **public** re-exports of the *former* `isaac.config.loader` public surface
   (`normalize-config`, `env`, `clear-env-overrides!`, `set-env-override!`, and
   any other symbol external modules currently require) are **allowed for this
   bean only**, so published agent/agent-spec (gitlibs) keep loading. Removal of
   those temporary public re-exports is **isaac-a7c0** (downstream cutover) —
   not a gate on flgy.
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

## Planner resolution (2026-08-03, prowl) — option 2: temporary public re-exports allowed; cutover is isaac-a7c0

The conflict is real and structural: a foundation-only clean cutover cannot
land while published agent (gitlibs) still requires the old
`isaac.config.loader/*` surface. Expanding this bean into a multi-module
cutover would invert the split's scope (pure foundation refactor) and couple
it to release/pin choreography it does not own.

**Decision: option 2 — amend AC #4 (done above).**

- **In scope for flgy:** the responsibility split, 1:1 specs, SCRAP clean,
  foundation `bb ci` green, **no foundation-internal shims**. Temporary
  **public** re-exports of the former loader public surface are allowed and
  expected so agent/agent-spec keep loading.
- **Out of scope for flgy / follow-up:** re-pointing all downstream callers and
  deleting the temporary public re-exports — that is **isaac-a7c0** (todo).
  flgy does **not** wait on a7c0.
- Do **not** leave the split unpushed waiting on multi-module rewires. Commit +
  push the foundation branch with the temporary public re-exports in place;
  hand to verify against the amended AC.

This is the same shape as the 1tce resolution (temporary public re-exports +
downstream cutover bean), and matches the "parity / don't hold green work
hostage to out-of-scope debt" precedent (dt9h, 7ivl, l70j, k1po, la8h, tki3).

### Worker actions now

1. Ensure temporary public re-exports on `isaac.config.loader` cover every
   former public symbol external modules still require (at minimum
   `normalize-config`, `env`, `clear-env-overrides!`, `set-env-override!` —
   expand if agent-spec needs more).
2. Confirm foundation `bb ci` green **with** those re-exports and **without**
   foundation-internal shims.
3. Commit + push `bean/isaac-flgy`; hand to verify against amended AC #1–#5.
4. Do **not** start the multi-module re-point in this bean — that is a7c0.

## Implementation (scrapper@isaac-work-1) — 2026-08-03

Planner option 2 applied. Pushed foundation `bean/isaac-flgy` @ `de4f2cf`.

**Split:**
- `isaac.config.env` (43) — env-overrides*, lock-dotenv!, env, clear/set-env-override!
- `isaac.config.parse` (91) — EDN/YAML/frontmatter, ${VAR} walk, fs primitives
- `isaac.config.companions` (72) — inline-vs-.md for souls/cron/hooks/hail
- `isaac.config.entities` (273) — entity-dir scan, overlay, finalize/merge, dangling md
- `isaac.config.normalize` (137) — normalize-config + section normalizers
- `isaac.config.warnings` (106) — top-level/root-entity/unknown-key/table warnings
- `isaac.config.loader` (390) — load orchestration + snapshot + workspace only

**AC #4 temporary public re-exports on loader** (removed by isaac-a7c0):
`env-overrides*`, `clear-env-overrides!`, `set-env-override!`, `env`, `normalize-config`.
No foundation-internal shims — foundation callers re-pointed to owning nses.

**Specs:** 1:1 for each new ns; former loader_spec describes moved (load_result,
normalize, companions, entities, parse, semantic_errors, module_discovery,
kit/foundry schema, signal_slots). SCRAP: no MANUAL_SPLIT.

**Verify:** `bb spec` 852/0; `bb features` 139/0; `bb lint` 0 errors;
`bb config-bypass-lint` ok. Pre-existing `check_compose_spec` fs failure also
on main (not introduced here).

**Out of scope:** multi-module cutover → isaac-a7c0.


## Landed (scrapper@isaac-work-1, 2026-08-03) — planner option 2

**Foundation head:** `de4f2cf8389acf1f28085a03163a11e24ec9f90b` on `bean/isaac-flgy`.

### Done
1. **Split** into `env`, `parse`, `companions`, `entities`, `normalize`, `warnings`;
   `loader` reduced to orchestration + snapshot + workspace (~390 lines).
2. **AC #2 1:1 specs:** env/parse/companions/entities/normalize/warnings/loader_spec
   (plus moved describes: load_result, kit/foundry/signal/module_discovery/semantic_errors).
3. **AC #4 amended:** temporary **public** re-exports on `isaac.config.loader`:
   `normalize-config`, `env`, `clear-env-overrides!`, `set-env-override!`, `env-overrides*`.
   Foundation-internal callers use owning namespaces (`api`, `main`, `cli.common`, specs).
   Removal is **isaac-a7c0**.
4. **bb ci** @ de4f2cf: specs `852/0` (1526 assertions), features `139/0` (342 assertions).
5. **SCRAP** on split specs: HIGH/MEDIUM/STABLE only — **no MANUAL_SPLIT**.
6. No multi-module re-point (a7c0). Branch pushed.

### Verify against
- Amended AC #1–#5 (public re-exports allowed for former loader surface).
- Commit **de4f2cf** only.
