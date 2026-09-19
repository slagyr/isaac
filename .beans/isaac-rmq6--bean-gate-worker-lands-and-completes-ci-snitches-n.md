---
# isaac-rmq6
title: 'Bean Gate: worker lands and completes; CI snitches; no verify crew'
status: draft
type: epic
priority: high
tags:
    - process
    - beans
    - ci
created_at: 2026-09-19T19:34:44Z
updated_at: 2026-09-19T19:34:44Z
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

## Children

Split when this leaves draft: (1) `bb bean-gate` + fixtures, (2) planner overlay + hail-plan one-liner, (3) `hail-bean-work-gate` + hail band, (4) isaac snitch CI, (5) cut over / drain verify.
