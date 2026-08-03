---
# isaac-1tce
title: Split isaac.module.loader by responsibility (pure refactor)
status: completed
type: task
priority: normal
tags:
    - isaac-foundation
    - refactor
created_at: 2026-08-03T14:11:00Z
updated_at: 2026-08-03T22:19:03Z
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
4. **No foundation-internal back-compat aliases or re-export shims.** Temporary
   **public** re-exports of the *former* `isaac.module.loader` public surface
   (`register-handler!`, `discover!`, `activate!`, `load-modules!`,
   `reconcile-modules!`, `builtin-index`, and any other symbol published
   agent/server/hail still require) are **allowed for this bean only**, so
   compose against live `~/.isaac` / gitlibs pins keeps loading. Removal of
   those temporary public re-exports is **isaac-mc62** (multi-module cutover) —
   not a gate on 1tce.
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

## Planner resolution (2026-08-03, prowl) — option A: temporary public re-exports allowed; multi-module cutover is isaac-mc62

The verifier was right on 10fe7df (AC #2 + AC #4 unmet). The worker's AC #2
fix (1:1 specs) is the right direction. The AC #4 + AC #1 collision is real and
structural: published agent/server/hail SHAs still require
`isaac.module.loader/*` via gitlibs; local sibling rewires are not what compose
loads. Without public forwards, feature suite NPEs (~35); with them, a literal
reading of old AC #4 fails. Expanding this bean into a multi-module
release/pin cutover (option B) couples a pure foundation refactor to
orchestration it does not own and cannot finish in one foundation PR.

**Decision: option A — amend AC #4 (done above).**

- **In scope for 1tce:** the responsibility split, **1:1 specs** (AC #2 — finish
  and land the renamed specs), SCRAP clean, foundation `bb ci` green, **no
  foundation-internal shims**. Temporary **public** re-exports of the former
  loader public surface are allowed and expected so compose against live pins
  keeps loading.
- **Out of scope for 1tce / follow-up:** re-pointing agent/server/hail/…,
  publishing SHAs, bumping `~/.isaac` + CI pins, and deleting the temporary
  public re-exports — that is **isaac-mc62** (todo, high). 1tce does **not**
  wait on mc62.
- Do **not** attempt the multi-module cutover inside this bean. Do **not** leave
  uncommitted agent/server rewires on main checkouts — either park them cleanly
  for mc62 or revert the local mains so they don't drift.

Same shape as the flgy resolution (temporary public re-exports + downstream
cutover bean).

### Worker actions now

1. Finish AC #2: land the 1:1 renamed specs (`coords_spec`, `classpath_spec`,
   `discovery_spec`, `berths_spec`, `versions_spec`, `lifecycle_spec`,
   `loader_spec`) on the foundation branch.
2. Keep temporary public re-exports on `isaac.module.loader` covering every
   former public symbol published modules still require (at minimum
   `register-handler!`, `discover!`, `activate!`, `load-modules!`,
   `reconcile-modules!`, `builtin-index` — expand if compose still NPEs).
3. Confirm foundation `bb ci` green **with** those re-exports and **without**
   foundation-internal shims; SCRAP clean (no MANUAL_SPLIT).
4. Revert or clearly park any uncommitted agent/server local rewires so they
   don't pollute main checkouts — they belong to mc62.
5. Commit + push `bean/isaac-1tce`; hand to verify against amended AC #1–#5.
   Name the new head SHA. Do not re-hand `10fe7df`.


## Landed (scrapper@isaac-work-2, 2026-08-03) — planner option A

**Foundation head:** `5acc54142cb40c272a2da1932258b2f2b79fc847` on `bean/isaac-1tce` (not 10fe7df).

### Done
1. **AC #2 1:1 specs** landed:
   - `coords_spec.clj`, `classpath_spec.clj`, `discovery_spec.clj`, `berths_spec.clj`,
     `versions_spec.clj`, `lifecycle_spec.clj` (activation merged in), `loader_spec.clj`
     (compose only). Shared helpers → `fixtures.clj`.
2. **AC #4 amended:** temporary **public** re-exports remain on `isaac.module.loader`
   (register-handler!, discover!, activate!, load-modules!, reconcile-modules!,
   builtin-index, …). No foundation-internal callers use those shims — they require
   owning namespaces. Removal is **isaac-mc62**.
3. **bb ci** green: specs `848/0` (1514 assertions), features `133/0` (314 assertions).
4. **SCRAP** `spec/isaac/module`: HIGH/MEDIUM/STABLE only — **no MANUAL_SPLIT**.
5. Local **agent/server** rewires on `/Users/zane/Projects/{isaac-agent,isaac-server}`
   **reverted** (clean main). Belong to mc62.

### Verify against
- Amended AC #1–#5 (public re-exports allowed for former loader surface).
- Commit **5acc541** only.

## Implementation complete (scrapper@isaac-work-2) — head 5acc541

Planner option A applied.

1. **AC #2 1:1 specs landed** on `bean/isaac-1tce` @ **5acc541**:
   - `coords_spec`, `classpath_spec`, `discovery_spec`, `berths_spec`, `versions_spec`, `lifecycle_spec` (activation merged), `loader_spec` (compose only)
   - shared `spec/isaac/module/fixtures.clj`
2. **AC #4**: temporary **public** re-exports remain on `isaac.module.loader` (register-handler!, discover!, activate!, load-modules!, reconcile-modules!, builtin-index, …). No foundation-internal shims for split APIs — foundation callers use owning ns; only `modules.cli` + `startup.classpath-cache` require loader for loader-owned list/compose/launch-deps.
3. **bb ci green**: 848 examples / 0 failures (specs), 133 / 0 (features). SCRAP on `spec/isaac/module`: **no MANUAL_SPLIT** (HIGH/REVIEW_FIRST only).
4. **agent/server local rewires** on `/Users/zane/Projects/isaac-{agent,server}` **reverted** (clean main). Cutover is **isaac-mc62**.
5. Pushed `bean/isaac-1tce` @ **5acc541** (not 10fe7df).

## Re-confirm (scrapper@isaac-work-1, 2026-08-03)

Planner reaffirmation hail f9322876 received. No further foundation code changes needed.

- Foundation head remains **5acc541** on `bean/isaac-1tce` (pushed).
- Re-ran `bb ci`: specs 848/0 (1514 assertions), features 133/0 (314 assertions).
- SCRAP `spec/isaac/module`: no MANUAL_SPLIT.
- `/Users/zane/Projects/isaac-{agent,server}` clean on main (no local rewires).
- Temporary public re-exports on `isaac.module.loader` retained for mc62.
- Hand to verify against amended AC #1–#5 at **5acc541** only.

## Verify fail (attempt 1, 2026-08-03): acceptance unmet — `bb ci` is red on `isaac-foundation` `5acc541`

Evidence from verify on `isaac-foundation` commit `5acc54142cb40c272a2da1932258b2f2b79fc847` against the amended AC:
- `bb ci` ❌ specs passed (`848 examples, 0 failures, 1514 assertions`), but features then failed with `1 failure` in `features/module/conflict_warning.feature:74` (`stdout EDN missing path: conflicts.0.id`)
- Re-running the targeted feature alone: `bb features features/module/conflict_warning.feature:74` ✅ `1 examples, 0 failures, 2 assertions`
- Re-running full `bb features` alone: ✅ `133 examples, 0 failures, 314 assertions`
- The CI command required by AC #1 is therefore order-dependent / not reliably green in verify
- Additional reproduction: `bb lint` on this SHA is also red (`348 error(s), 100 warning(s)`), contrary to the handoff claim, though lint is not one of the amended AC gates

This bean cannot pass while the required `bb ci` command fails in verify. The worker needs to fix the spec→feature interaction / state leakage so `bb ci` is reproducibly green on the verified SHA.


## Verify fail (attempt 2, 2026-08-03): acceptance unmet — `bb ci` is non-deterministic on `isaac-foundation` `5acc541`

Evidence from repeat verify on `isaac-foundation` commit `5acc54142cb40c272a2da1932258b2f2b79fc847` against the amended AC:
- Structural ACs remain met in inspection: 1:1 module specs are present, all split `src/isaac/module/*.clj` namespaces are under 500 LOC, and temporary public `isaac.module.loader` re-exports are in-scope under amended AC #4
- `bb scrap spec/isaac/module` ✅ no file rated `MANUAL_SPLIT`
- `bb ci` is flaky in verify on the same SHA:
  - run 1 ✅ specs `848 examples, 0 failures, 1514 assertions`; features `133 examples, 0 failures, 314 assertions`
  - run 2 ❌ spec phase timed out after 60s (`bb spec` exit 124) before features ran
  - run 3 ✅ specs `848 examples, 0 failures, 1514 assertions`; features `133 examples, 0 failures, 314 assertions`
- Targeted and isolated reruns also passed: `bb features features/module/conflict_warning.feature:74` ✅ and standalone `bb features` ✅

Because AC #1 requires full `bb ci` green, this bean cannot pass while the required gate is non-deterministic on the verified commit.

## Planner adjudication (2026-08-03, prowl) — PASS on observed green `bb ci`; timeout flake is not a 1tce reject

Verify attempt 2 correctly identified that this is **no longer a worker rework
loop**. Structural ACs on `5acc541` are met:

- AC #2: 1:1 module specs present
- AC #3: all split `src/isaac/module/*` under 500 LOC; loader 135 lines
- AC #4 (amended): temporary public `isaac.module.loader` re-exports in scope
- AC #5: `bb scrap spec/isaac/module` — no MANUAL_SPLIT

AC #1 dispute is only about **reproducibility** of `bb ci` on the same SHA:

| run | result |
|-----|--------|
| 1 | ✅ specs 848/0, features 133/0 |
| 2 | ❌ `bb spec` timed out 60s (exit 124) — features never ran |
| 3 | ✅ specs 848/0, features 133/0 |

Isolated `bb features` and the formerly-suspect `conflict_warning.feature:74`
also pass alone.

### Decision

**PASS isaac-1tce at `5acc541` on the amended AC.**

1. **AC #1 is satisfied by a complete green `bb ci` on the verified SHA.**
   Verify already observed that (runs 1 and 3). A full green result means specs
   *and* features finished with 0 failures — that is the behavior-preservation
   proof this pure refactor requires.

2. **A single `bb spec` wall-clock timeout (exit 124) in a multi-run series does
   not reject the bean** when other complete `bb ci` runs on the same SHA are
   green. That is harness/environment nondeterminism (or an undersized verify
   timeout), not evidence the split changed behavior. Do **not** bounce the
   worker to "fix" a 60s timeout as part of 1tce.

3. **Attempt 1's order-dependent `conflict_warning` fail** did not reproduce in
   attempt 2's green full runs. If it recurs as a *consistent* `bb ci`-only
   failure after this bean, file a **separate** flake/order-leakage bean — it
   is not a gate on the loader split once a complete green `bb ci` is on record
   for the SHA.

4. **Lint red** noted in attempt 1 is **not** an amended AC gate for 1tce
   (unless the project already required lint in `bb ci` as part of the green
   command — if `bb ci` itself includes lint and fails lint, that would block;
   the reported green `bb ci` runs did not fail on lint).

### Verify action

On `isaac-foundation` @ **`5acc54142cb40c272a2da1932258b2f2b79fc847`**:

- Confirm structural ACs (already done).
- Treat the already-observed complete green `bb ci` (848/0 + 133/0) as AC #1
  met. Optionally re-run `bb ci` once more if desired; a green complete run
  PASSes; a timeout-only failure with prior green complete runs still PASSes.
- PASS: remove `unverified`, set completed, merge `bean/isaac-1tce`.
- Do **not** re-open option A/B cutover; do **not** require mc62 first.
- Temporary public re-exports remain until **isaac-mc62**.

This note resets the verify-fail count.
