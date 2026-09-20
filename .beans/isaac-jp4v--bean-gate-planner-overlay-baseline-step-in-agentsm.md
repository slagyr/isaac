---
# isaac-jp4v
title: 'Bean Gate: planner overlay — baseline step in AGENTS.md Planning + dual-run'
status: in-progress
type: task
priority: high
tags:
    - process
    - beans
created_at: 2026-09-19T20:43:16Z
updated_at: 2026-09-20T04:35:48Z
parent: isaac-rmq6
blocked_by:
    - isaac-cy85
---

Repo: **isaac** (this repo). Child 2 of isaac-rmq6. Unblocked: isaac-cy85 landed `bb bean-gate` on main (7b3cab5).

Documentation only — no code. Do **not** touch `.toolbox/` (those files are fetched copies of agent-lib and the toolbox owns them), `.github/workflows/ci-tests.yml`, or the `beans` CLI.

## Why

`bb bean-gate baseline` exists but nothing tells a planner to run it, and nothing tells a verifier that a second check now exists. Until both are written down, the gate is dead code.

## Change 1 — `## Planning` gains a baseline step

Add a subsection (after `### Repo layout`) covering, in the planner's own order of work:

1. Co-author the scenarios as today, then commit them `@wip` to the **module's `main`**, not to a `bean/<id>` branch. Module CI excludes `@wip`, so main stays green and the worker's branch starts from a tree that already holds the contract.
2. From the isaac clone: `bb bean-gate baseline <bean-id> <repo>:<path>[:<line>…]`. Without line numbers every `@wip` scenario in the file belongs to the bean; with them, name each scenario by the line of its `Scenario:` keyword. `--dir <repo>=<path>` points at a checkout that is not `../<repo>`.
3. Commit the bean with the appended `feature-baseline:` / `feature-blob:` lines. The baseline commit must come from the planner — the gate fails a baseline introduced by a commit carrying an `Isaac-Session: isaac-work…` or `isaac-verify…` trailer.
4. A feature edit after baselining is made **on module main** and re-baselined (`baseline` appends new lines; the newest win). Never rewrite or delete an existing `feature-*` line — the gate walks the bean's git history and treats those lines, plus everything under `## Acceptance…` and `## Exceptions`, as append-only.

Fix `### Repo layout` in the same pass: it currently says a planning session commits "the `@wip` feature file in the relevant module repo", which is now specifically main.

## Change 2 — `## Bean Workflow` gains the dual-run paragraph

While both paths run: the worker still hands off `unverified` and hails `isaac-verify`, and the verifier **also** runs `bb bean-gate verify <id>` from the isaac clone before passing, recording one line in the bean — `bean-gate: pass` or `bean-gate: FAIL — <first failure>`. Exit 2 (`no feature-baseline`) means the bean predates the gate: note `bean-gate: not gated` and verify as usual. A gate failure on an otherwise-good bean is a **fail**, returned to the worker.

State plainly that this is temporary: isaac-przv moves landing to the worker and isaac-e20m retires the verify hail.

## Acceptance

- `AGENTS.md` carries both changes; a reader who knows nothing else can baseline a bean from it.
- Every command in the new text runs as written against main: `bb bean-gate --help`, `bb bean-gate verify <some-ungated-bean>` exits 2 with the documented message.
- `git diff --name-only origin/main` lists **only** `AGENTS.md` (plus the bean). Nothing under `.toolbox/`.
- Wording matches the shipped CLI (`baseline` appends and does not commit; `verify` exits 0/1/2).

Process bean: no product code, no scenarios. TDD rules are suspended (see the hail-bean-work skill's process-test section).

Dispatched: hail 0cfedd0e 2026-09-20T04:35:30Z (band isaac-work)
