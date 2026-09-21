---
# isaac-rmq6
title: 'Bean Gate: worker lands and completes; CI snitches; no verify crew'
status: completed
type: epic
priority: high
tags:
    - process
    - beans
    - ci
created_at: 2026-09-19T19:34:44Z
updated_at: 2026-09-21T17:41:33Z
---

Repo: **isaac** (beans tracker). Do **not** change the `beans` CLI. Do **not** edit agent-lib `plan.md` / `work.md` / `hail-bean-work` (Zanebot other projects toolbox those URLs).

## Why

Verify is a second full session (and bounce loops are worse). Keep the adversarial **contract freeze**; drop the verifier **crew**. Worker implements, tests, gates, lands, may `completed`. Isaac CI catches cheaters.

## Decisions (2026-09-19, Micah)

1. **One bb command** in this repo: `bb bean-gate baseline <id>` and `bb bean-gate verify <id>`.
2. **Planner records baseline** — after `@wip` scenarios are on the module `main`, run `baseline` (writes `feature-baseline:` / per-file blob SHAs). No hand-typed SHAs. Missing baseline → old verify path.
3. **Gate** — (a) contract fields in `.beans/<id>.md` match **first introduction** in git history (`feature-baseline`, acceptance file:line selectors, `## Exceptions`); (b) `git diff <baseline> HEAD -- <files>` is only `@wip` removal plus planner `## Exceptions`; (c) fail if baseline missing.
4. **Gate fail** — worker **reverts** the `.feature` **or** hails plan. Worker must **not** add `## Exceptions`.
5. **Trust but verify** — if local `bb bean-gate verify` is green, worker may `completed` (and do today’s verify land: squash, pins, `main-sha`). No `unverified`, no hail to verify. CI is the snitch, not a second crew.
6. **Bean history** — freeze contract fields only. Status, notes, checkpoints, `main-sha` may change.
7. **Skills** — new **names**, Isaac-only toolbox. `work-bean-gate` command + `hail-bean-work-gate` skill in this repo (raw.githubusercontent.com/slagyr/isaac/…). Planner overlay: `AGENTS.md` `## Planning` (run `bb bean-gate baseline`). **Isaac hail** `isaac-work` loads `hail-bean-work-gate`, not `hail-bean-work`. Orchestration-work unchanged.
8. **In-flight** — no `feature-baseline` → old unverified/verify path. Don’t rewrite open beans.

## Rollout

1. `bb.edn` + `scripts/bean_gate.clj` (or equivalent) + fixtures. New GHA later; do **not** un-freeze the old monolith `ci-tests.yml`.
2. Planner habit / `## Planning` + `record-baseline`. Dual-run: workers still hail verify; verifier also runs `bb bean-gate verify`.
3. Worker close: `hail-bean-work-gate` + AGENTS.md Bean Workflow.
4. Snitch CI: workflow on `main` pushes that touch `.beans/`; for each **completed** bean in the push, fetch the module and run `bb bean-gate verify`. First version: fail the workflow and ping; reopen later if we want.
5. Cut over: drain `unverified`; stop hailing `isaac-verify`. Leave orchestration process-tests until we decide they should match.

## Non-goals

Generic beans CLI. Changing agent-lib. Deleting the verify band on day one. Foundation locks / worksite (separate). Orchestration process-test rewrite.

## Design (settled with Micah, 2026-09-19)

Refines decision 3. Full mechanics live in isaac-cy85.

- **Blob baseline.** `bb bean-gate baseline` appends `feature-baseline: <repo> <main-sha>` and `feature-blob: <repo> <path> <blob> [<lines>]`. The blob is the frozen "before" text; the gate diffs it against the file at the checked commit. It also lets CI fetch shallow.
- **Contract lines are append-only.** `feature-*` lines and the lines under `## Acceptance…` / `## Exceptions` may be appended, never edited or removed, from the first baseline onward. A planner-authorized feature edit is made on main and re-baselined (new lines append), so feature-text exceptions no longer need prose.
- **Two feature checks.** Every baselined block (header, Background, each scenario), `@wip` stripped on both sides, appears verbatim in the checked file; other beans' added scenarios are fine. The worker's own diff (bean branch vs merge-base, or the `main-sha` squash commit) changes `.feature` files only by removing `@wip`.
- **Planner scenarios go to module main** before baselining; module CI excludes `@wip`.

## Children

1. isaac-cy85 — `bb bean-gate` + fixtures (todo)
2. isaac-jp4v — planner overlay + dual-run (draft, blocked by cy85)
3. isaac-przv — `hail-bean-work-gate` + isaac-work band (draft, blocked by cy85)
4. isaac-4b21 — snitch CI (draft, blocked by cy85)
5. isaac-e20m — cut over / drain verify (draft, blocked by jp4v, przv, 4b21)

## Closed (2026-09-21, planner)

All seven children are `completed` and the rollout's five steps are done. The
gate is the default path: the worker implements, runs `bb bean-gate verify`,
lands its own bean and marks it `completed`; CI re-gates every completed,
baselined bean that reaches main. No verify crew in the loop.

| child | what it shipped | main-sha (isaac) |
|-------|-----------------|------------------|
| isaac-cy85 | `bb bean-gate` + fixtures | 53ea1cf |
| isaac-jp4v | planner overlay + dual-run | 721f26c |
| isaac-przv | `hail-bean-work-gate` + `isaac-work` band cutover | 0eb8bc7 |
| isaac-4b21 | snitch CI (`.github/workflows/bean-gate.yml`) | 19f77f2 |
| isaac-e20m | cutover — AGENTS.md default, drain unverified | c04bf70 |
| isaac-dopm | gate lines count only at column 0 | d4ba96e |
| isaac-9yms | every verdict names the tree it checked | 7849bd3 |

`bb ci` in isaac at close: 57 examples, 0 failures, 85 assertions.

### What the dogfood was worth

The four process children all shipped without a single bean being baselined
through them, so the dual run proved only the *old* path. Everything the gate
actually got wrong surfaced in the first two real runs, after cutover:

- the snitch swallowed its own first FAIL (dead verdict logic under `bash -e`)
  and the clone loop parsed baselines differently from the gate — both fixed on
  e20m (`5aabd1fe`);
- a `feature-baseline:` quoted in prose silently gated the quoting bean
  (isaac-dopm);
- `verify` read whatever branch a shared sibling was parked on and never said
  so, turning one parked worktree into five confident, entirely spurious
  failures (isaac-9yms).

The lesson for the next process epic: a gate that has never gated anything is
not shipped, whatever its suites say. Baseline the dogfood bean first.

### Band text (unversioned, fixed on zanebot)

`przv` switched `~/.isaac/config/hail/isaac-work.md` line 6 to load
`hail-bean-work-gate` but left the old unconditional "When handing off to
verify, hail the verify-band…" paragraph below it, so the band contradicted the
skill it loads. Now qualified: a gated bean needs no verify hail (gate exit 0 →
land and complete yourself); the handoff applies only at exit 2. Backup at
`isaac-work.md.bak-20260921-verifyline`; `isaac config validate` → OK and
`isaac hail send --band isaac-work --dry-run` still renders.

These `isaac-*` bands are **not** version-controlled: `orchestration/isaac-beans/
install.sh` deploys only the `orchestration-*` bands and `_orchestration-template`.
Worth its own bean if we want them tracked.

### Left standing, as decided

The `isaac-verify` band, the `perceptor` crew and the orchestration
process-tests stay. `isaac-verify` is still the live path for ungated beans —
which these two fixes both were.
